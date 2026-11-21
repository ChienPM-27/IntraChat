package vn.intrachat.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Device identity is a stable UUID; a display name is purely presentation.
 * Do not confuse a UUID supplied over the network with authenticated identity.
 */
public record PeerIdentity(UUID deviceId, String displayName) {
    public PeerIdentity {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(displayName, "displayName");
        displayName = displayName.strip();
        if (displayName.isEmpty() || displayName.length() > 128 || displayName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("displayName must contain 1..128 non-control characters");
        }
    }

    public PeerIdentity withDisplayName(String nextName) {
        return new PeerIdentity(deviceId, nextName);
    }
}
