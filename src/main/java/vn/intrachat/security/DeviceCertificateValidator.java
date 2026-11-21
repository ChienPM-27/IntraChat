package vn.intrachat.security;

import java.security.*;
import java.security.cert.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;

/** Inspects candidate certificates; an unknown valid certificate is STILL NOT TRUSTED. */
public final class DeviceCertificateValidator {
    private DeviceCertificateValidator() { }
    public static void validateCandidate(X509Certificate certificate) throws GeneralSecurityException {
        if(certificate==null) throw new CertificateException("Missing certificate");
        certificate.checkValidity();
        certificate.verify(certificate.getPublicKey()); // self-issued key possession/signature
        if(certificate.getBasicConstraints()>=0)throw new CertificateException("CA certificate not allowed");
        boolean[] usage=certificate.getKeyUsage();
        if(usage==null||usage.length==0||!usage[0])throw new CertificateException("Digital signature key usage required");
        List<String> extended=certificate.getExtendedKeyUsage();
        if(extended==null||!extended.contains("1.3.6.1.5.5.7.3.1")
             ||!extended.contains("1.3.6.1.5.5.7.3.2"))
            throw new CertificateException("Mutual client/server auth usage required");
        PublicKey key=certificate.getPublicKey();
        if(!(key instanceof ECPublicKey ec)||ec.getParams().getCurve().getField().getFieldSize()!=256)
            throw new CertificateException("P-256 EC key required");
        // Also check exact standardized curve generator/order, not merely field size.
        try {
            AlgorithmParameters expected=AlgorithmParameters.getInstance("EC");
            expected.init(new ECGenParameterSpec("secp256r1"));
            var p=expected.getParameterSpec(java.security.spec.ECParameterSpec.class);
            if(!ec.getParams().getGenerator().equals(p.getGenerator())
               || !ec.getParams().getOrder().equals(p.getOrder())
               || ec.getParams().getCofactor()!=p.getCofactor())
                throw new CertificateException("Unexpected EC curve parameters");
        } catch(java.security.spec.InvalidParameterSpecException e){
            throw new CertificateException("Unsupported EC curve",e);
        }
        if(!"SHA256withECDSA".equalsIgnoreCase(certificate.getSigAlgName()))
            throw new CertificateException("Unsupported signing algorithm");
    }
    public static boolean matchesPinnedKey(X509Certificate certificate,byte[] pinned) throws GeneralSecurityException {
        validateCandidate(certificate);
        return FingerprintVerifier.sameFingerprint(FingerprintVerifier.spkiFingerprint(certificate.getPublicKey()),pinned);
    }
}
