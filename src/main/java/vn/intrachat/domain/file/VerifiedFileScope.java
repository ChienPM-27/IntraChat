package vn.intrachat.domain.file;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import vn.intrachat.domain.P2PConversation;

/** Pure exact-two-peer scope. Actual TLS certificate/pin/SAS verification is required upstream. */
public final class VerifiedFileScope {
    private final UUID conversationId;
    private final UUID first;
    private final UUID second;
    private final byte[] firstPin;
    private final byte[] secondPin;

    private VerifiedFileScope(UUID first, byte[] firstPin, UUID second, byte[] secondPin) {
        this.first = first;
        this.second = second;
        this.firstPin = firstPin.clone();
        this.secondPin = secondPin.clone();
        this.conversationId = P2PConversation.idFor(first, this.firstPin, second, this.secondPin);
    }

    public static VerifiedFileScope p2p(UUID a, byte[] aPin, UUID b, byte[] bPin) {
        Objects.requireNonNull(a, "first device");
        Objects.requireNonNull(b, "second device");
        if (a.equals(b) || a.equals(new UUID(0,0)) || b.equals(new UUID(0,0)))
            throw new IllegalArgumentException("Distinct nonzero device IDs required");
        requirePin(aPin);
        requirePin(bPin);
        return new VerifiedFileScope(a,aPin,b,bPin);
    }

    private static void requirePin(byte[] pin) {
        if (pin == null || pin.length != 32) throw new IllegalArgumentException("Pinned SPKI SHA-256 must be 32 bytes");
    }

    public UUID conversationId() { return conversationId; }

    /** A necessary identity predicate, NOT proof that a current M2 TLS session is authenticated. */
    public boolean allows(UUID deviceId, byte[] verifiedSpkiSha256) {
        if (deviceId == null || verifiedSpkiSha256 == null || verifiedSpkiSha256.length != 32)
            return false;
        return (deviceId.equals(first) && MessageDigest.isEqual(firstPin, verifiedSpkiSha256))
                || (deviceId.equals(second) && MessageDigest.isEqual(secondPin, verifiedSpkiSha256));
    }
}
