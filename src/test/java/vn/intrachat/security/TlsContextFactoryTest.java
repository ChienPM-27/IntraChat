package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TlsContextFactoryTest {
    @TempDir Path dir;
    @Test void onlyValidCandidateAndPinnedKeysMayHandshake() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        PinnedPeerStore pins=new PinnedPeerStore(dir.resolve("pins.bin"));
        TlsContextFactory candidate=new TlsContextFactory(a,pins,b.deviceId(),true);
        assertFalse(candidate.isTrustedBeforeUserComparison());
        assertTrue(candidate.context().getSupportedSSLParameters().getProtocols().length>0);
        candidate.validatePeerCertificate(b.certificate());
        pins.pinNew(b.deviceId(),b.fingerprint());
        TlsContextFactory pinned=new TlsContextFactory(a,pins,b.deviceId(),false);
        pinned.validatePeerCertificate(b.certificate());
        assertTrue(pinned.isTrustedBeforeUserComparison());
        DeviceIdentity impostor=DeviceCertificateFactory.create(b.deviceId());
        assertThrows(java.security.cert.CertificateException.class,
            ()->pinned.validatePeerCertificate(impostor.certificate()));
        assertThrows(java.security.cert.CertificateException.class,
            ()->new TlsContextFactory(a,new PinnedPeerStore(dir.resolve("empty.bin")),
                b.deviceId(),false).validatePeerCertificate(b.certificate()));
    }
}
