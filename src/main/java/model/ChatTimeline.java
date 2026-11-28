package model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChatTimeline {
    private final String conversationId;
    private final Map<String, ChatMessage> messages = new HashMap<>();
    private final Map<String, DeliveryState> earlyReceipts = new HashMap<>();

    public ChatTimeline(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean merge(ChatMessage message) {
        if (!conversationId.equals(message.conversationId())) {
            return false;
        }
        DeliveryState early = earlyReceipts.remove(message.id());
        ChatMessage existing = messages.get(message.id());
        ChatMessage merged = existing == null ? message : existing.withState(message.state());
        // File offers have a placeholder path; a later transfer update carries the actual
        // destination. Do not let an older SENDING snapshot overwrite a completed path.
        if (existing != null && existing.content().type() != MessageType.TEXT
                && existing.content().type() == message.content().type()
                && merged.state() == message.state()
                && !existing.content().equals(message.content())) {
            merged = new ChatMessage(existing.id(), existing.conversationId(), existing.senderId(),
                existing.sentAt(), message.content(), merged.state());
        }
        if (early != null) {
            merged = merged.withState(early);
        }
        messages.put(merged.id(), merged);
        return existing != merged;
    }

    public boolean advanceState(String messageId, DeliveryState state) {
        ChatMessage existing = messages.get(messageId);
        if (existing == null) {
            earlyReceipts.merge(messageId, state, DeliveryState::advanceTo);
            return false;
        }
        ChatMessage updated = existing.withState(state);
        messages.put(messageId, updated);
        return updated != existing;
    }

    public List<ChatMessage> orderedMessages() {
        return messages.values().stream().sorted(ChatMessage.ORDER).toList();
    }
}
