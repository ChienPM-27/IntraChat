package model;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public record ChatMessage(String id, String conversationId, String senderId, Instant sentAt,
                          MessageContent content, DeliveryState state) {
    public static final Comparator<ChatMessage> ORDER = Comparator.comparing(ChatMessage::sentAt)
            .thenComparing(ChatMessage::id);

    public ChatMessage {
        Objects.requireNonNull(id);
        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(senderId);
        Objects.requireNonNull(sentAt);
        Objects.requireNonNull(content);
        Objects.requireNonNull(state);
    }

    public ChatMessage withState(DeliveryState updated) {
        DeliveryState next = state.advanceTo(updated);
        return next == state ? this : new ChatMessage(id, conversationId, senderId, sentAt, content, next);
    }
}
