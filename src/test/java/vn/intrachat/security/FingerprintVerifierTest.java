package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintVerifierTest {
    @Test void bothPeersSeeSameCodeAndTranscriptChangesIt() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        byte[] an=new byte[32];byte[] bn=new byte[32];
        java.util.Arrays.fill(an,(byte)1);java.util.Arrays.fill(bn,(byte)2);
        String first=FingerprintVerifier.sharedCode(a.deviceId(),a.fingerprint(),an,
                    b.deviceId(),b.fingerprint(),bn);
        String reversed=FingerprintVerifier.sharedCode(b.deviceId(),b.fingerprint(),bn,
                    a.deviceId(),a.fingerprint(),an);
        assertEquals(first,reversed);
        assertTrue(first.matches("[0-9A-F]{4}(-[0-9A-F]{4}){3}"));
        bn[0]=3;
        assertNotEquals(first,FingerprintVerifier.sharedCode(a.deviceId(),a.fingerprint(),an,
                    b.deviceId(),b.fingerprint(),bn));
    }
    @Test void pinComparisonIsStrict() throws Exception {
        DeviceIdentity peer=DeviceCertificateFactory.create(UUID.randomUUID());
        byte[] pin=peer.fingerprint();
        assertTrue(FingerprintVerifier.sameFingerprint(pin,peer.fingerprint()));
        byte[] bad=pin.clone();bad[0]^=1;
        assertFalse(FingerprintVerifier.sameFingerprint(pin,bad));
        assertFalse(FingerprintVerifier.sameFingerprint(pin,new byte[2]));
        assertThrows(IllegalArgumentException.class,()->FingerprintVerifier.sharedCode(
            peer.deviceId(),pin,new byte[3],UUID.randomUUID(),pin,new byte[32]));
    }
}
