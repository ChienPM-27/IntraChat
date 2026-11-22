package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import vn.intrachat.protocol.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Typed receipts are not trust proofs: protected transport must still verify identity/session. */
class DeliveryReceiptDomainTest {
    private final UUID a=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private final UUID b=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private final UUID session=UUID.randomUUID(),conversation=UUID.randomUUID(),original=UUID.randomUUID();
    private ChatV2Message ack(Map<String,Object> data){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,session,b,a,
            conversation,Instant.now(),data);
    }
    private ChatV2Message read(Map<String,Object> data){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,session,b,a,
            conversation,Instant.now(),data);
    }
    @Test void typedAckIsAcceptedToRamNotProofOfForegroundView(){
        var wire=ack(Map.of("originalMessageId",original.toString(),
            "acceptedAtUTC","2026-11-22T06:00:00Z","result","ACCEPTED"));
        var receipt=DeliveryReceipt.from(wire);
        assertEquals(original,receipt.originalMessageId());
        assertEquals(session,receipt.sessionId());
        assertEquals(b,receipt.verifiedSenderClaim());
        assertEquals(a,receipt.destinationId());
        assertEquals(Instant.parse("2026-11-22T06:00:00Z"),receipt.acceptedAtUTC());
        var attempt=new MessageAttempt(original);
        attempt.startSending();attempt.localWriteCompleted();
        attempt.onDeliveryAck();
        assertEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,attempt.status());
        assertNotEquals(MessageAttempt.Status.READ,attempt.status());
    }
    @Test void typedReadRequiresASeparateViewEventAndKeepsItsOwnId(){
        UUID eventId=UUID.randomUUID();
        var wire=read(Map.of("originalMessageId",original.toString(),
            "readEventId",eventId.toString(),"viewedAtUTC","2026-11-22T06:05:00Z"));
        var receipt=ReadReceipt.from(wire);
        assertEquals(original,receipt.originalMessageId());
        assertEquals(eventId,receipt.readEventId());
        assertEquals(conversation,receipt.conversationId());
        assertEquals(Instant.parse("2026-11-22T06:05:00Z"),receipt.viewedAtUTC());
        var attempt=new MessageAttempt(original);
        attempt.startSending();attempt.localWriteCompleted();
        attempt.onViewReceipt();
        assertEquals(MessageAttempt.Status.READ,attempt.status());
        attempt.onDeliveryAck();
        assertEquals(MessageAttempt.Status.READ,attempt.status());
    }
    @Test void wrongTypeZeroReferenceWrongResultOrMalformedDateCannotBecomeReceipt(){
        assertThrows(IllegalArgumentException.class,()->DeliveryReceipt.from(read(Map.of(
            "originalMessageId",original.toString(),"readEventId",UUID.randomUUID().toString(),
            "viewedAtUTC","2026-11-22T06:05:00Z"))));
        assertThrows(IllegalArgumentException.class,()->ReadReceipt.from(ack(Map.of(
            "originalMessageId",original.toString(),"acceptedAtUTC",Instant.now().toString(),
            "result","ACCEPTED"))));
        assertThrows(IllegalArgumentException.class,()->DeliveryReceipt.from(ack(Map.of(
            "originalMessageId",new UUID(0,0).toString(),
            "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED"))));
        assertThrows(IllegalArgumentException.class,()->DeliveryReceipt.from(ack(Map.of(
            "originalMessageId",original.toString(),"acceptedAtUTC",Instant.now().toString(),
            "result","CLAIMED_READ"))));
        assertThrows(IllegalArgumentException.class,()->ReadReceipt.from(read(Map.of(
            "originalMessageId",original.toString(),
            "readEventId",UUID.randomUUID().toString(),"viewedAtUTC","INVALID"))));
    }
}
