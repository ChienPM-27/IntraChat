package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.*;
import vn.intrachat.security.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group frame authorization is NOT JSON parsing. Verified TLS peer/session and
 * bilateral V3 capability must be bound to signed owner roster and per-message grant.
 */
class GroupV1AuthorizationEnvelopeTest {
    private static KeyPair pair()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }
    private final GroupV1Codec codec=new GroupV1Codec();
    private final UUID owner=UUID.randomUUID(),sender=UUID.randomUUID(),
        recipient=UUID.randomUUID(),session=UUID.randomUUID(),group=UUID.randomUUID(),
        message=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-22T10:00:00Z");
    private record Fixtures(GroupMetadata metadata,KeyPair key,GroupGrant grant,byte[] sig,
                            GroupV1Message text,GroupV1AuthorizationEnvelope.Channel channel){}
    private Fixtures fixture()throws Exception {
        KeyPair key=pair();
        byte[] ownerPin=FingerprintVerifier.spkiFingerprint(key.getPublic());
        byte[] senderPin=new byte[32],recipientPin=new byte[32];
        Arrays.fill(senderPin,(byte)3);Arrays.fill(recipientPin,(byte)4);
        GroupIdentity id=new GroupIdentity(group,"Nhóm tác vụ",owner,ownerPin);
        GroupRoster roster=new GroupRoster(id,1,new byte[32],List.of(
            new GroupMember(owner,ownerPin,GroupMember.Role.OWNER),
            new GroupMember(sender,senderPin,GroupMember.Role.MEMBER),
            new GroupMember(recipient,recipientPin,GroupMember.Role.MEMBER)),
            UUID.randomUUID(),now.minusSeconds(2));
        GroupMetadata signed=new GroupMetadata(roster,GroupOwnerSignature.signRoster(
            roster,key.getPrivate(),key.getPublic()),false);
        GroupGrant grant=new GroupGrant(group,message,owner,ownerPin,1,
            GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster)),
            sender,List.of(owner,recipient),now.minusSeconds(1),now.plusSeconds(9),new byte[16]);
        byte[] sig=GroupOwnerSignature.signGrant(grant,key.getPrivate(),key.getPublic());
        GroupV1Message text=new GroupV1Message(message,GroupV1Type.GROUP_TEXT,session,
            sender,recipient,group,1,now,Map.of("text","Tin kín 🧪",
                "grantCanonicalBase64",Base64.getEncoder().encodeToString(
                    GroupCanonicalBytes.grant(grant)),
                "ownerSignatureBase64",Base64.getEncoder().encodeToString(sig)));
        GroupV1AuthorizationEnvelope.Channel channel=new GroupV1AuthorizationEnvelope.Channel(
            session,sender,recipient,senderPin,true,true);
        return new Fixtures(signed,key,grant,sig,text,channel);
    }
    @Test void groupTextNeedsMatchingTlsSessionCapabilityOwnerSignedRosterAndGrant()throws Exception {
        var f=fixture();
        var tested=codec.decode(codec.encode(f.text()));
        assertDoesNotThrow(()->GroupV1AuthorizationEnvelope.authorize(
            tested,f.channel(),f.metadata(),f.key().getPublic(),now));
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            tested,new GroupV1AuthorizationEnvelope.Channel(session,sender,recipient,
                f.channel().verifiedPeerSpkiSha256(),true,false),
            f.metadata(),f.key().getPublic(),now));
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            tested,new GroupV1AuthorizationEnvelope.Channel(session,sender,recipient,
                f.channel().verifiedPeerSpkiSha256(),false,true),
            f.metadata(),f.key().getPublic(),now));
    }
    @Test void spoofedTlsPeerSessionDestinationAndRoleAreRejected()throws Exception{
        var f=fixture();
        byte[] fakePin=f.channel().verifiedPeerSpkiSha256();fakePin[0]^=1;
        var badKeys=List.of(
            new GroupV1AuthorizationEnvelope.Channel(UUID.randomUUID(),sender,recipient,
                f.channel().verifiedPeerSpkiSha256(),true,true),
            new GroupV1AuthorizationEnvelope.Channel(session,UUID.randomUUID(),recipient,
                f.channel().verifiedPeerSpkiSha256(),true,true),
            new GroupV1AuthorizationEnvelope.Channel(session,sender,UUID.randomUUID(),
                f.channel().verifiedPeerSpkiSha256(),true,true),
            new GroupV1AuthorizationEnvelope.Channel(session,sender,recipient,
                fakePin,true,true));
        for(var bad:badKeys)
            assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
                f.text(),bad,f.metadata(),f.key().getPublic(),now));
    }
    @Test void forgedOwnerGrantWrongRevisionMissingMemberAndExpiredGrantNeverAuthorize()throws Exception {
        var f=fixture();
        KeyPair fakeOwner=pair();
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            f.text(),f.channel(),f.metadata(),fakeOwner.getPublic(),now));
        GroupV1Message wrongRevision=new GroupV1Message(message,GroupV1Type.GROUP_TEXT,
            session,sender,recipient,group,2,now,f.text().payload());
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            wrongRevision,f.channel(),f.metadata(),f.key().getPublic(),now));
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            f.text(),f.channel(),f.metadata(),f.key().getPublic(),now.plusSeconds(11)));
        Map<String,Object> forged=new LinkedHashMap<>(f.text().payload());
        forged.put("text","Mạo danh owner");
        forged.put("ownerSignatureBase64",Base64.getEncoder().encodeToString(new byte[70]));
        GroupV1Message forgedText=new GroupV1Message(message,GroupV1Type.GROUP_TEXT,
            session,sender,recipient,group,1,now,forged);
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            forgedText,f.channel(),f.metadata(),f.key().getPublic(),now));
    }
    @Test void ackAndErrorNeverMasqueradeAsAuthorizedGroupText()throws Exception {
        var f=fixture();
        GroupV1Message ack=new GroupV1Message(UUID.randomUUID(),
            GroupV1Type.GROUP_DELIVERY_ACK,session,sender,recipient,group,1,now,
            Map.of("originalMessageId",message.toString(),"acceptedAtUTC",now.toString(),
                "result","ACCEPTED"));
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            ack,f.channel(),f.metadata(),f.key().getPublic(),now),
            "An ACK must pass its own per-recipient outbound correlation logic");
        GroupV1Message error=new GroupV1Message(UUID.randomUUID(),
            GroupV1Type.GROUP_ERROR,session,sender,recipient,group,1,now,
            Map.of("originalMessageId",message.toString(),"errorCode","NOT_MEMBER"));
        assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
            error,f.channel(),f.metadata(),f.key().getPublic(),now));
        assertEquals("GROUP_AUTHORIZATION_FAILED",
            assertThrows(ProtocolException.class,()->GroupV1AuthorizationEnvelope.authorize(
                ack,f.channel(),f.metadata(),f.key().getPublic(),now)).code());
    }
}
