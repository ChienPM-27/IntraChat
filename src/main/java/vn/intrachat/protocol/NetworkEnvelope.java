package vn.intrachat.protocol;

import vn.intrachat.domain.PeerIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable internal representation of a M1 technical message, never an authenticated principal. */
public record NetworkEnvelope(
        int protocolVersion,
        UUID messageId,
        MessageType type,
        PeerIdentity sender,
        Destination destination,
        Instant sentAt,
        Map<String, Object> payload) {
    public static final UUID UNKNOWN_PEER = new UUID(0, 0);

    public NetworkEnvelope {
        if (protocolVersion != 1) throw new IllegalArgumentException("Only protocol v1 is supported");
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(sentAt, "sentAt");
        Objects.requireNonNull(payload, "payload");
        payload = Map.copyOf(payload);
    }

    public static NetworkEnvelope create(MessageType type, PeerIdentity sender, UUID destinationId,
                                         Map<String, Object> payload) {
        return new NetworkEnvelope(1, UUID.randomUUID(), type, sender,
            new Destination(DestinationKind.PEER, destinationId), Instant.now(), payload);
    }
}
