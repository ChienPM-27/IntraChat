package vn.intrachat.security;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeviceCertificateValidatorTest {
    private static KeyPair keys(String curve) throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(curve));
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(KeyPair subjectKeys, KeyPair signerKeys,
            Instant notBefore, Instant notAfter, boolean ca, int usage,
            boolean mutualEku, boolean unexpectedCritical) throws Exception {
        X500Name name=new X500Name("CN="+UUID.randomUUID());
        var builder=new JcaX509v3CertificateBuilder(name,BigInteger.valueOf(101),
                Date.from(notBefore),Date.from(notAfter),name,subjectKeys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage,true,new KeyUsage(usage));
        builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(
                mutualEku
                  ? new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth,KeyPurposeId.id_kp_clientAuth}
                  : new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth}));
        if(unexpectedCritical) builder.addExtension(
                new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.999"),true,DERNull.INSTANCE);
        return new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(signerKeys.getPrivate())));
    }

    @Test void validCandidateButChangedPinnedKeyFails() throws Exception {
        DeviceIdentity a=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(a.deviceId());
        assertDoesNotThrow(()->DeviceCertificateValidator.validateCandidate(a.certificate()));
        assertTrue(DeviceCertificateValidator.matchesPinnedKey(a.certificate(),a.fingerprint()));
        assertFalse(DeviceCertificateValidator.matchesPinnedKey(b.certificate(),a.fingerprint()));
        assertFalse(DeviceCertificateValidator.matchesPinnedKey(a.certificate(),new byte[0]));
    }

    @Test void expiredAndNotYetValidCertificatesFailClosed() throws Exception {
        KeyPair key=keys("secp256r1");
        Instant now=Instant.now();
        X509Certificate expired=certificate(key,key,now.minusSeconds(7200),
                now.minusSeconds(3600),false,KeyUsage.digitalSignature,true,false);
        X509Certificate future=certificate(key,key,now.plusSeconds(3600),
                now.plusSeconds(7200),false,KeyUsage.digitalSignature,true,false);
        assertThrows(CertificateException.class,()->DeviceCertificateValidator.validateCandidate(expired));
        assertThrows(CertificateException.class,()->DeviceCertificateValidator.validateCandidate(future));
    }

    @Test void badSignatureWrongCurveAndMissingUsagesFailClosed() throws Exception {
        KeyPair key=keys("secp256r1");
        KeyPair impostor=keys("secp256r1");
        Instant now=Instant.now();
        X509Certificate forged=certificate(key,impostor,now.minusSeconds(60),
                now.plusSeconds(3600),false,KeyUsage.digitalSignature,true,false);
        KeyPair p384=keys("secp384r1");
        X509Certificate wrongCurve=certificate(p384,p384,
                now.minusSeconds(60),now.plusSeconds(3600),false,
                KeyUsage.digitalSignature,true,false);
        X509Certificate badUsage=certificate(key,key,now.minusSeconds(60),
                now.plusSeconds(3600),false,KeyUsage.keyEncipherment,true,false);
        X509Certificate badEku=certificate(key,key,now.minusSeconds(60),
                now.plusSeconds(3600),false,KeyUsage.digitalSignature,false,false);
        X509Certificate ca=certificate(key,key,now.minusSeconds(60),
                now.plusSeconds(3600),true,KeyUsage.digitalSignature,true,false);
        for(X509Certificate rejected:new X509Certificate[]{forged,wrongCurve,badUsage,badEku,ca})
            assertThrows(GeneralSecurityException.class,
                    ()->DeviceCertificateValidator.validateCandidate(rejected));
    }

    @Test void unknownCriticalExtensionMustNotBeIgnored() throws Exception {
        KeyPair key=keys("secp256r1");
        Instant now=Instant.now();
        X509Certificate unexpected=certificate(key,key,now.minusSeconds(60),
                now.plusSeconds(3600),false,KeyUsage.digitalSignature,true,true);
        assertTrue(unexpected.hasUnsupportedCriticalExtension());
        assertThrows(CertificateException.class,
                ()->DeviceCertificateValidator.validateCandidate(unexpected));
    }
}
