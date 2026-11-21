package vn.intrachat.security;

import javax.net.ssl.*;
import java.net.Socket;
import java.io.IOException;
import java.security.*;
import java.security.cert.*;
import java.util.*;

/**
 * JSSE TLSv1.3 mutual device certificate context.
 * Unknown valid first-contact peers are QUARANTINED, never authorized for user data
 * until the application verifies human SAS and BOTH parties finish pairing.
 */
public final class TlsContextFactory {
    private final SSLContext context;
    private final PinnedPeerStore trust;
    private final UUID expectedRemoteId;
    private final boolean permitUnpairedCandidate;

    public TlsContextFactory(DeviceIdentity local, PinnedPeerStore trust,
                             UUID expectedRemoteId, boolean permitUnpairedCandidate)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(local);
        this.trust=Objects.requireNonNull(trust);
        this.expectedRemoteId=Objects.requireNonNull(expectedRemoteId);
        this.permitUnpairedCandidate=permitUnpairedCandidate;
        KeyStore keystore=KeyStore.getInstance("PKCS12");
        char[] password=UUID.randomUUID().toString().toCharArray();
        try {
            keystore.load(null,password);
            keystore.setKeyEntry("device",local.keyPair().getPrivate(),password,
                                 new java.security.cert.Certificate[]{local.certificate()});
            KeyManagerFactory kmf=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore,password);
            context=SSLContext.getInstance("TLSv1.3");
            context.init(kmf.getKeyManagers(),new TrustManager[]{new PinningManager()},new SecureRandom());
        } finally { Arrays.fill(password,'\0'); }
    }
    public SSLContext context(){return context;}
    public boolean isTrustedBeforeUserComparison() throws IOException {
        return trust.known(expectedRemoteId);
    }

    /** Certificate candidate validation is not app authorization. */
    public void validatePeerCertificate(X509Certificate certificate)throws CertificateException {
        try {
            DeviceCertificateValidator.validateCandidate(certificate);
            String expectedCN="CN="+expectedRemoteId;
            if(!certificate.getSubjectX500Principal().getName().equalsIgnoreCase(expectedCN)
               || !certificate.getIssuerX500Principal().equals(certificate.getSubjectX500Principal()))
                throw new CertificateException("Claimed device ID differs from certificate identity");
            byte[] fingerprint=FingerprintVerifier.spkiFingerprint(certificate.getPublicKey());
            if(trust.known(expectedRemoteId)){
                if(!trust.matches(expectedRemoteId,fingerprint))
                    throw new CertificateException("Pinned peer public key changed");
            }else if(!permitUnpairedCandidate) {
                throw new CertificateException("Unknown peer requires manual fingerprint verification");
            }
            // Caller is still responsible for human consent + SAS two-sided ready before CHAT_TEXT.
        }catch(CertificateException e){throw e;}
        catch(Exception failure){throw new CertificateException("Peer identity validation failed",failure);}
    }

    private final class PinningManager extends X509ExtendedTrustManager {
        private void validate(X509Certificate[] chain)throws CertificateException{
            if(chain==null||chain.length!=1||chain[0]==null)
                throw new CertificateException("Exactly one device identity leaf required");
            validatePeerCertificate(chain[0]);
        }
        @Override public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{validate(chain);}
        @Override public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{validate(chain);}
        @Override public void checkClientTrusted(X509Certificate[] chain,String authType,Socket socket)throws CertificateException{validate(chain);}
        @Override public void checkServerTrusted(X509Certificate[] chain,String authType,Socket socket)throws CertificateException{validate(chain);}
        @Override public void checkClientTrusted(X509Certificate[] chain,String authType,SSLEngine engine)throws CertificateException{validate(chain);}
        @Override public void checkServerTrusted(X509Certificate[] chain,String authType,SSLEngine engine)throws CertificateException{validate(chain);}
        @Override public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
    }
}
