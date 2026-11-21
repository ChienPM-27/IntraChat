package model;

import java.util.Objects;

public record ChatUser(String id, String displayName, boolean online) {
    public ChatUser {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        if (id.isBlank() || displayName.isBlank()) {
            throw new IllegalArgumentException("Tên và định danh người dùng không được để trống.");
        }
    }
}
