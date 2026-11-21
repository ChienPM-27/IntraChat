package model;

import java.util.List;
import java.util.Map;

public record ChatSnapshot(List<ChatUser> users, List<Conversation> conversations,
                           Map<String, ChatMessage> previews, List<FriendRequest> friendRequests) {
    public ChatSnapshot {
        users = List.copyOf(users);
        conversations = List.copyOf(conversations);
        previews = Map.copyOf(previews);
        friendRequests = List.copyOf(friendRequests);
    }

    public ChatSnapshot(List<ChatUser> users, List<Conversation> conversations, Map<String, ChatMessage> previews) {
        this(users, conversations, previews, List.of());
    }
}
