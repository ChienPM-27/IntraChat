package vn.intrachat.protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-independent metadata draft for future messages.
 * Not a serializer, not an authorization mechanism, not a network message handler.
 */
public record MessageMetadata(
        int protocolVersion,
        UUID messageId,
        MessageType type,
        UUID senderDeviceId,
        Destination destination,
        Instant sentAt) {
    public MessageMetadata {
        if (protocolVersion != 1) {
            throw new IllegalArgumentException("Only draft protocol version 1 metadata is supported");
        }
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(senderDeviceId, "senderDeviceId");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sentAt, "sentAt");
    }
}
