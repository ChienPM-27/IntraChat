package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.security.Signature;
import static org.junit.jupiter.api.Assertions.*;

class DeviceIdentityTest {
    @Test void freshDeviceUsesStrongP256AndValidSelfSignedCertificate() throws Exception {
        DeviceIdentity identity=DeviceCertificateFactory.create(UUID.randomUUID());
        assertEquals("EC",identity.keyPair().getPublic().getAlgorithm());
        assertEquals(identity.deviceId().toString(),identity.certificate().getSubjectX500Principal().getName().split("CN=")[1].split(",")[0]);
        assertEquals(32,identity.fingerprint().length);
        DeviceCertificateValidator.validateCandidate(identity.certificate());
        Signature signature=Signature.getInstance("SHA256withECDSA");
        signature.initSign(identity.keyPair().getPrivate());
        signature.update("test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] proof=signature.sign();
        signature.initVerify(identity.certificate().getPublicKey());
        signature.update("test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(signature.verify(proof));
    }
    @Test void newDeviceKeyMustHaveDifferentFingerprint() throws Exception {
        UUID id=UUID.randomUUID();
        DeviceIdentity a=DeviceCertificateFactory.create(id);
        DeviceIdentity b=DeviceCertificateFactory.create(id);
        assertFalse(java.util.Arrays.equals(a.fingerprint(),b.fingerprint()));
        assertFalse(DeviceCertificateValidator.matchesPinnedKey(a.certificate(),b.fingerprint()));
    }
}
