package model;

import java.util.List;
import java.util.Map;

public record ChatSnapshot(List<ChatUser> users, List<Conversation> conversations,
                           Map<String, ChatMessage> previews) {
    public ChatSnapshot {
        users = List.copyOf(users);
        conversations = List.copyOf(conversations);
        previews = Map.copyOf(previews);
    }
}
