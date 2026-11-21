package vn.intrachat.domain.group;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Verified device identity; display name is presentation-only, not an ACL key. */
public record GroupMember(UUID deviceId, byte[] pinnedSpkiSha256, Role role,
                          String displayName) {
    public enum Role { OWNER, MEMBER }

    public GroupMember {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(pinnedSpkiSha256, "pinnedSpkiSha256");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(displayName, "displayName");
        if (deviceId.equals(new UUID(0L, 0L)))
            throw new IllegalArgumentException("Zero deviceId");
        if (pinnedSpkiSha256.length != 32)
            throw new IllegalArgumentException("SPKI SHA-256 must be 32 bytes");
        if (displayName.isBlank() || displayName.codePointCount(0, displayName.length()) > 128)
            throw new IllegalArgumentException("Invalid member alias");
        if (displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 512)
            throw new IllegalArgumentException("Member alias too large");
        for (int i = 0; i < displayName.length();) {
            char c = displayName.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= displayName.length() ||
                    !Character.isLowSurrogate(displayName.charAt(i + 1)))
                    throw new IllegalArgumentException("Unpaired high surrogate in member alias");
                i += 2;
            } else {
                if (Character.isLowSurrogate(c) || Character.isISOControl(c))
                    throw new IllegalArgumentException("Invalid character in member alias");
                i++;
            }
        }
        pinnedSpkiSha256 = pinnedSpkiSha256.clone();
    }

    @Override public byte[] pinnedSpkiSha256() {
        return pinnedSpkiSha256.clone();
    }

    @Override public boolean equals(Object other) {
        return other instanceof GroupMember b &&
            deviceId.equals(b.deviceId) &&
            Arrays.equals(pinnedSpkiSha256, b.pinnedSpkiSha256) &&
            role == b.role && displayName.equals(b.displayName);
    }

    @Override public int hashCode() {
        return Objects.hash(deviceId, Arrays.hashCode(pinnedSpkiSha256), role, displayName);
    }
}
