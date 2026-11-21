package vn.intrachat.domain.group;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A group UUID is a nonsecret identifier, never a membership or trust capability. */
public record GroupIdentity(UUID groupId, String displayName, Instant createdAt) {
    public static final int MAX_NAME_CODE_POINTS = 128;
    public static final int MAX_NAME_BYTES = 512;

    public GroupIdentity {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
        if (groupId.equals(new UUID(0L, 0L)))
            throw new IllegalArgumentException("Reserved groupId");
        if (createdAt.equals(Instant.EPOCH) || createdAt.isBefore(Instant.EPOCH))
            throw new IllegalArgumentException("Invalid group creation time");
        int length = displayName.codePointCount(0, displayName.length());
        if (displayName.isBlank() || length > MAX_NAME_CODE_POINTS ||
            displayName.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES)
            throw new IllegalArgumentException("Invalid group name length");
        for (int i = 0; i < displayName.length();) {
            char c = displayName.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= displayName.length() ||
                    !Character.isLowSurrogate(displayName.charAt(i + 1)))
                    throw new IllegalArgumentException("Unpaired high surrogate");
                i += 2;
            } else {
                if (Character.isLowSurrogate(c))
                    throw new IllegalArgumentException("Unpaired low surrogate");
                if (Character.isISOControl(c))
                    throw new IllegalArgumentException("Control character in group name");
                i++;
            }
        }
    }

    public static GroupIdentity create(String displayName) {
        return new GroupIdentity(UUID.randomUUID(), displayName, Instant.now());
    }
}
