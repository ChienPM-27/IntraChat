package model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record Conversation(String id, String title, boolean group, List<String> memberIds) {
    public Conversation {
        Objects.requireNonNull(id);
        Objects.requireNonNull(title);
        memberIds = List.copyOf(memberIds);
        if (id.isBlank() || title.isBlank() || memberIds.size() < 2 || (!group && memberIds.size() != 2)
                || new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new IllegalArgumentException("Thông tin hội thoại không hợp lệ.");
        }
    }
}
