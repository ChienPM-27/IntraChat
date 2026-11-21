package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintVerifierTest {
    @Test void deterministicCanonicalBinaryTestVector() {
        UUID a=UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID b=UUID.fromString("22222222-2222-4222-8222-222222222222");
        byte[] fpA=new byte[32],fpB=new byte[32],nonceA=new byte[32],nonceB=new byte[32];
        for(int i=0;i<32;i++){fpA[i]=(byte)i;fpB[i]=(byte)(i+32);
            nonceA[i]=(byte)(i+64);nonceB[i]=(byte)(i+96);}
        assertEquals("ac818b2d88b58cf914e943963bd4921e307769494b75c427f68f89b0374789dc",
            java.util.HexFormat.of().formatHex(FingerprintVerifier.transcriptHash(a,fpA,nonceA,b,fpB,nonceB)));
        assertEquals("AC81-8B2D-88B5-8CF9",
            FingerprintVerifier.sharedCode(a,fpA,nonceA,b,fpB,nonceB));
        assertEquals("AC81-8B2D-88B5-8CF9",
            FingerprintVerifier.sharedCode(b,fpB,nonceB,a,fpA,nonceA));
        fpB[0]^=1;
        assertNotEquals("AC81-8B2D-88B5-8CF9",
            FingerprintVerifier.sharedCode(a,fpA,nonceA,b,fpB,nonceB));
    }
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
