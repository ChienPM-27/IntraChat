package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupOwnerSignatureTest {
    static KeyPair pair()throws Exception{
        var gen=KeyPairGenerator.getInstance("EC");gen.initialize(new ECGenParameterSpec("secp256r1"));return gen.generateKeyPair();
    }
    static GroupRoster rosterFor(PublicKey ownerKey) {
        byte[] fp=FingerprintVerifier.spkiFingerprint(ownerKey);
        var identity=new GroupIdentity(GroupCanonicalBytesTest.GROUP,"Do an",GroupCanonicalBytesTest.OWNER,fp);
        return new GroupRoster(identity,1,new byte[32],
            List.of(new GroupMember(GroupCanonicalBytesTest.OWNER,fp,GroupMember.Role.OWNER),
                new GroupMember(GroupCanonicalBytesTest.PEER,GroupCanonicalBytesTest.fingerprint(32),GroupMember.Role.MEMBER)),
            GroupCanonicalBytesTest.EVENT,GroupCanonicalBytesTest.INSTANT);
    }
    @Test void signedRosterIsBoundToPinnedOwnerAndContent()throws Exception{
        KeyPair kp=pair();GroupRoster roster=rosterFor(kp.getPublic());
        byte[] signature=GroupOwnerSignature.signRoster(roster,kp.getPrivate(),kp.getPublic());
        assertTrue(GroupOwnerSignature.verifyRoster(roster,signature,kp.getPublic()));
        assertFalse(GroupOwnerSignature.verifyRoster(roster,signature,pair().getPublic()));
        var tampered=new GroupRoster(new GroupIdentity(roster.identity().groupId(),"Other",roster.identity().ownerDeviceId(),
            roster.identity().ownerSpkiSha256()),1,new byte[32],roster.members(),roster.commitEventId(),roster.committedAt());
        assertFalse(GroupOwnerSignature.verifyRoster(tampered,signature,kp.getPublic()));
        signature[0]^=1;assertFalse(GroupOwnerSignature.verifyRoster(roster,signature,kp.getPublic()));
    }
    @Test void revisedRosterRequiresPreviousCanonicalHashAndHigherRevision()throws Exception{
        KeyPair kp=pair();GroupRoster initial=rosterFor(kp.getPublic());
        byte[] first=GroupOwnerSignature.signRoster(initial,kp.getPrivate(),kp.getPublic());
        GroupMetadata current=new GroupMetadata(initial,first,true);
        assertTrue(GroupOwnerSignature.acceptCommit(current,null,kp.getPublic()));
        byte[] priorHash=GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(initial));
        GroupRoster next=new GroupRoster(initial.identity(),2,priorHash,initial.members(),UUID.randomUUID(),Instant.now());
        GroupMetadata update=new GroupMetadata(next,GroupOwnerSignature.signRoster(next,kp.getPrivate(),kp.getPublic()),true);
        assertTrue(GroupOwnerSignature.acceptCommit(update,current,kp.getPublic()));
        assertFalse(GroupOwnerSignature.acceptCommit(current,update,kp.getPublic()));
        GroupRoster wrongPrev=new GroupRoster(initial.identity(),2,new byte[32],initial.members(),UUID.randomUUID(),Instant.now());
        assertFalse(GroupOwnerSignature.acceptCommit(new GroupMetadata(wrongPrev,
            GroupOwnerSignature.signRoster(wrongPrev,kp.getPrivate(),kp.getPublic()),true),current,kp.getPublic()));
        assertFalse(GroupOwnerSignature.acceptCommit(update,current,pair().getPublic()));
    }
    @Test void grantIsBoundToVerifiedOwnerAndCannotAuthorizeOtherRecipient()throws Exception{
        KeyPair kp=pair();GroupRoster roster=rosterFor(kp.getPublic());
        byte[] nonce=new byte[16];Arrays.fill(nonce,(byte)1);
        GroupGrant grant=new GroupGrant(roster.identity().groupId(),UUID.randomUUID(),roster.identity().ownerDeviceId(),
            roster.identity().ownerSpkiSha256(),1,GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster)),
            roster.identity().ownerDeviceId(),List.of(GroupCanonicalBytesTest.PEER),Instant.now(),Instant.now().plusSeconds(5),nonce);
        byte[] sig=GroupOwnerSignature.signGrant(grant,kp.getPrivate(),kp.getPublic());
        assertTrue(GroupOwnerSignature.verifyGrant(grant,sig,kp.getPublic()));
        assertFalse(GroupOwnerSignature.verifyGrant(grant,sig,pair().getPublic()));
        assertThrows(IllegalArgumentException.class,()->new GroupGrant(grant.groupId(),grant.messageId(),
            grant.ownerDeviceId(),grant.ownerSpkiSha256(),1,grant.rosterHash(),grant.senderId(),
            List.of(grant.senderId()),grant.issuedAt(),grant.expiresAt(),nonce));
    }
}
