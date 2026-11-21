package vn.intrachat.protocol;

import java.time.Instant;
import java.util.*;

/** Strict protected-channel v2 envelope; parsing alone does NOT authenticate its claimed sender. */
public record ChatV2Message(UUID messageId, ChatV2Type type, UUID sessionId,
        UUID senderId, UUID destinationId, UUID conversationId, Instant sentAt,
        Map<String,Object> payload) {
    public ChatV2Message {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(senderId);
        Objects.requireNonNull(destinationId);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(sentAt);
        payload=Map.copyOf(Objects.requireNonNull(payload));
    }
}
