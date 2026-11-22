package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Only an authenticated signed grant may insert text into Group RAM. Delivery
 * ACK is constructed AFTER that insertion, never on TLS socket write.
 */
class GroupGrantValidationTest {
    private final UUID owner=UUID.randomUUID(),sender=UUID.randomUUID(),
        recipient=UUID.randomUUID(),group=UUID.randomUUID(),session=UUID.randomUUID();
    private final Instant now=Instant.parse("2026-11-22T12:00:00Z");
    private final KeyPair ownerKey;
    private final GroupMetadata roster;
    private final GroupV1AuthorizationEnvelope.Channel channel;
    GroupGrantValidationTest()throws Exception{
        KeyPairGenerator gen=KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        ownerKey=gen.generateKeyPair();
        byte[] spki=FingerprintVerifier.spkiFingerprint(ownerKey.getPublic());
        byte[] senderPin=new byte[32],destPin=new byte[32];
        Arrays.fill(senderPin,(byte)19);Arrays.fill(destPin,(byte)21);
        GroupRoster signed=new GroupRoster(new GroupIdentity(group,"Group Inbox",owner,spki),
            1,new byte[32],List.of(new GroupMember(owner,spki,GroupMember.Role.OWNER),
                new GroupMember(sender,senderPin,GroupMember.Role.MEMBER),
                new GroupMember(recipient,destPin,GroupMember.Role.MEMBER)),
            UUID.randomUUID(),now.minusSeconds(2));
        roster=new GroupMetadata(signed,GroupOwnerSignature.signRoster(
            signed,ownerKey.getPrivate(),ownerKey.getPublic()),false);
        channel=new GroupV1AuthorizationEnvelope.Channel(session,sender,recipient,
            senderPin,true,true);
    }
    private GroupV1Message text(UUID msgId,String text,byte[] nonce)throws Exception{
        GroupGrant grant=new GroupGrant(group,msgId,owner,
            FingerprintVerifier.spkiFingerprint(ownerKey.getPublic()),
            1,GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster.roster())),
            sender,List.of(owner,recipient),now.minusSeconds(1),now.plusSeconds(9),nonce);
        return new GroupV1Message(msgId,GroupV1Type.GROUP_TEXT,session,sender,recipient,
            group,1,now,Map.of("text",text,
                "grantCanonicalBase64",Base64.getEncoder().encodeToString(
                    GroupCanonicalBytes.grant(grant)),
                "ownerSignatureBase64",Base64.getEncoder().encodeToString(
                    GroupOwnerSignature.signGrant(grant,ownerKey.getPrivate(),ownerKey.getPublic()))));
    }
    @Test void acceptedIntoRamYieldsOnlyPerRecipientRealDeliveryAck()throws Exception{
        try(var box=new GroupInboundMailbox()){
            UUID id=UUID.randomUUID();
            var frame=text(id,"Xin chào nhóm 🌻",new byte[16]);
            var accepted=box.accept(frame,channel,roster,ownerKey.getPublic(),now);
            assertEquals(1,box.transcript(group).size());
            assertEquals("Xin chào nhóm 🌻",box.transcript(group).getFirst().text());
            assertEquals(GroupV1Type.GROUP_DELIVERY_ACK,accepted.ack().type());
            assertEquals(id.toString(),accepted.ack().payload().get("originalMessageId"));
            assertEquals(sender,accepted.ack().destinationId());
            assertEquals(recipient,accepted.ack().senderId());
            assertEquals(session,accepted.ack().sessionId());
            assertThrows(ProtocolException.class,()->box.accept(frame,
                channel,roster,ownerKey.getPublic(),now),"Replay cannot produce another ACK");
            assertEquals(1,box.transcript(group).size());
            box.close();
            assertTrue(box.transcript(group).isEmpty(),"RAM-only Group content erased on close");
        }
    }
    @Test void invalidSignerTlsSessionAndStaleRosterNeverCreateBubbleOrAck()throws Exception{
        try(var box=new GroupInboundMailbox()){
            var valid=text(UUID.randomUUID(),"nội dung riêng",new byte[16]);
            var broken=new HashMap<>(valid.payload());
            broken.put("ownerSignatureBase64",Base64.getEncoder().encodeToString(new byte[70]));
            var forged=new GroupV1Message(valid.messageId(),valid.type(),
                valid.sessionId(),valid.senderId(),valid.destinationId(),
                valid.groupId(),valid.membershipRevision(),now,broken);
            assertThrows(ProtocolException.class,()->box.accept(forged,
                channel,roster,ownerKey.getPublic(),now));
            var wrong=new GroupV1AuthorizationEnvelope.Channel(UUID.randomUUID(),
                sender,recipient,channel.verifiedPeerSpkiSha256(),true,true);
            assertThrows(ProtocolException.class,()->box.accept(valid,
                wrong,roster,ownerKey.getPublic(),now));
            assertThrows(ProtocolException.class,()->box.accept(valid,
                channel,new GroupMetadata(roster.roster(),roster.ownerSignature(),true),
                ownerKey.getPublic(),now));
            assertThrows(ProtocolException.class,()->box.accept(valid,
                channel,roster,ownerKey.getPublic(),now.plusSeconds(20)));
            assertTrue(box.transcript(group).isEmpty());
            assertEquals(0,box.acceptedCount());
        }
    }
    @Test void differentMessageWithReplayedOwnerNonceIsRejectedWithoutAck()throws Exception{
        try(var box=new GroupInboundMailbox()){
            byte[] nonce=new byte[16];nonce[0]=7;
            box.accept(text(UUID.randomUUID(),"message one",nonce),
                channel,roster,ownerKey.getPublic(),now);
            assertThrows(ProtocolException.class,()->box.accept(
                text(UUID.randomUUID(),"message two",nonce),
                channel,roster,ownerKey.getPublic(),now));
            assertEquals(1,box.acceptedCount());
        }
    }
    @Test void oldGrantFromRevisionOneCannotDeliverAfterOwnerRemovesSenderAtRevisionTwo()
        throws Exception{
        var original=text(UUID.randomUUID(),"Chưa được gửi sau khi bị loại",new byte[16]);
        byte[] ownerPin=FingerprintVerifier.spkiFingerprint(ownerKey.getPublic());
        GroupRoster removed=new GroupRoster(roster.roster().identity(),2,
            GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster.roster())),
            List.of(new GroupMember(owner,ownerPin,GroupMember.Role.OWNER),
                new GroupMember(recipient,new byte[32],GroupMember.Role.MEMBER)),
            UUID.randomUUID(),now);
        var current=new GroupMetadata(removed,GroupOwnerSignature.signRoster(
            removed,ownerKey.getPrivate(),ownerKey.getPublic()),false);
        try(var box=new GroupInboundMailbox()){
            assertThrows(ProtocolException.class,()->box.accept(original,channel,current,
                ownerKey.getPublic(),now));
            assertTrue(box.transcript(group).isEmpty());
            assertEquals(0,box.acceptedCount());
        }
    }
    @Test void maxFiveHundredMessagesPerGroupAndCloseErase()throws Exception{
        try(var box=new GroupInboundMailbox()){
            for(int n=0;n<500;n++){
                byte[] nonce=new byte[16];
                java.nio.ByteBuffer.wrap(nonce).putInt(n);
                box.accept(text(UUID.randomUUID(),"m"+n,nonce),
                    channel,roster,ownerKey.getPublic(),now);
            }
            assertEquals(500,box.transcript(group).size());
            assertThrows(ProtocolException.class,()->box.accept(
                text(UUID.randomUUID(),"too much",new byte[16]),
                channel,roster,ownerKey.getPublic(),now));
            assertEquals(500,box.acceptedCount());
        }
    }
}
