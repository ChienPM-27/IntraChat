package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Wire-only receipt validation; peer/session correlation happens inside TLS and LiveChatService. */
class ReadReceiptContractTest {
    private final ChatV2Codec codec=new ChatV2Codec();

    private ChatV2Message receipt(String original,String event,String viewedAt) {
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),Instant.now(),
                Map.of("originalMessageId",original,"readEventId",event,
                        "viewedAtUTC",viewedAt));
    }

    @Test void validReadHasSeparateEnvelopeOriginalEventAndUtcTimestamp() throws Exception {
        UUID original=UUID.randomUUID(), event=UUID.randomUUID();
        ChatV2Message value=receipt(original.toString(),event.toString(),
                "2026-11-21T10:00:00Z");
        assertEquals(ChatV2Type.READ_RECEIPT,codec.decode(codec.encode(value)).type());
        assertEquals(original.toString(),codec.decode(codec.encode(value))
                .payload().get("originalMessageId"));
    }
    @Test void readEventTimestampMustBeUtcZNotOffsetOrGarbage() throws Exception {
        UUID original=UUID.randomUUID(),event=UUID.randomUUID();
        assertThrows(ProtocolException.class,()->codec.encode(receipt(
                original.toString(),event.toString(),"2026-11-21T17:00:00+07:00")));
        assertThrows(ProtocolException.class,()->codec.encode(receipt(
                original.toString(),event.toString(),"Saturday")));
    }
    @Test void zeroOrMalformedReceiptIdentifiersCannotBeAccepted() throws Exception {
        String valid=UUID.randomUUID().toString();
        String zero=new UUID(0,0).toString();
        assertThrows(ProtocolException.class,()->codec.encode(receipt(zero,valid,
                "2026-11-21T10:00:00Z")));
        assertThrows(ProtocolException.class,()->codec.encode(receipt(valid,zero,
                "2026-11-21T10:00:00Z")));
        assertThrows(ProtocolException.class,()->codec.encode(receipt(valid,"not-a-uuid",
                "2026-11-21T10:00:00Z")));
    }
    @Test void readCannotUseDeliveryAckFields() throws Exception {
        UUID original=UUID.randomUUID();
        ChatV2Message wrong=new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                Instant.now(),Map.of("originalMessageId",original.toString(),
                        "acceptedAtUTC","2026-11-21T10:00:00Z","result","ACCEPTED"));
        assertThrows(ProtocolException.class,()->codec.encode(wrong));
    }
}
