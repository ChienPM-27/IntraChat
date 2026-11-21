package vn.intrachat.security;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** JDK P-256 identity key and a self-signed X509 certificate. Does NOT itself grant remote trust. */
public final class DeviceCertificateFactory {
    private DeviceCertificateFactory() { }
    public static DeviceIdentity create(UUID deviceId) throws GeneralSecurityException {
        if (deviceId == null) throw new IllegalArgumentException("Missing deviceId");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"),new SecureRandom());
            KeyPair keys = generator.generateKeyPair();
            return new DeviceIdentity(deviceId,keys,certificate(deviceId,keys));
        } catch (Exception problem) {
            if(problem instanceof GeneralSecurityException e) throw e;
            throw new GeneralSecurityException("Could not generate device certificate",problem);
        }
    }
    private static X509Certificate certificate(UUID deviceId,KeyPair keys) throws Exception {
        X500Name subject=new X500Name("CN="+deviceId);
        SecureRandom random=new SecureRandom();
        BigInteger serial=new BigInteger(160,random).setBit(159);
        Instant now=Instant.now();
        var builder=new JcaX509v3CertificateBuilder(subject,serial,
          Date.from(now.minusSeconds(60)),Date.from(now.plusSeconds(365L*86400)),subject,keys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage,true,new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(
          new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth,KeyPurposeId.id_kp_clientAuth}));
        var signer=new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
        var cert=new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        cert.verify(keys.getPublic());
        DeviceCertificateValidator.validateCandidate(cert);
        return cert;
    }
}
