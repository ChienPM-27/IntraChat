package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageMetadataTest {
    private MessageMetadata valid() {
        return new MessageMetadata(1, UUID.randomUUID(), MessageType.CHAT_TEXT,
            UUID.randomUUID(), new Destination(DestinationKind.PEER, UUID.randomUUID()),
            Instant.parse("2026-11-21T06:00:00Z"));
    }
    @Test void requiredFieldsArePreserved() {
        MessageMetadata metadata = valid();
        assertEquals(1, metadata.protocolVersion());
        assertEquals(MessageType.CHAT_TEXT, metadata.type());
        assertEquals(DestinationKind.PEER, metadata.destination().kind());
        assertEquals(Instant.parse("2026-11-21T06:00:00Z"), metadata.sentAt());
    }
    @Test void invalidMetadataFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new MessageMetadata(0, UUID.randomUUID(), MessageType.CHAT_TEXT,
            UUID.randomUUID(), new Destination(DestinationKind.PEER, UUID.randomUUID()), Instant.now()));
        assertThrows(NullPointerException.class, () -> new MessageMetadata(1, UUID.randomUUID(), null,
            UUID.randomUUID(), new Destination(DestinationKind.PEER, UUID.randomUUID()), Instant.now()));
        assertThrows(NullPointerException.class, () -> new Destination(null, UUID.randomUUID()));
    }
    @Test void utf8MessageIsRepresentableWithoutConversion() {
        String sample = "Xin chào Hoài 📨";
        assertEquals(sample, new String(sample.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
    }
}
