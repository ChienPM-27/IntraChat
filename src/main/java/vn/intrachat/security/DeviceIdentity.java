package vn.intrachat.security;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.UUID;

/** Device-local key ownership. A claimed device UUID is not trusted until its key is pinned. */
public record DeviceIdentity(UUID deviceId, KeyPair keyPair, X509Certificate certificate) {
    public DeviceIdentity {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(keyPair);
        Objects.requireNonNull(certificate);
    }
    public byte[] fingerprint() {
        return FingerprintVerifier.spkiFingerprint(certificate.getPublicKey());
    }
}
