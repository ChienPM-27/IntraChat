package vn.intrachat.security;

import com.sun.jna.platform.win32.Crypt32Util;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DeviceIdentityStoreBindingTest {
    @TempDir Path dir;

    /** Tampered local DPAPI blob is still not permission to impersonate a different local UUID. */
    @Test void certificateCnMustEqualStoredUuidNotMerelyContainIt() throws Exception {
        UUID stored=UUID.randomUUID();
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keys=generator.generateKeyPair();
        X500Name misleading=new X500Name("CN=shadow-"+stored);
        Instant now=Instant.now();
        var builder=new JcaX509v3CertificateBuilder(misleading,BigInteger.valueOf(779),
                Date.from(now.minusSeconds(60)),Date.from(now.plusSeconds(3600)),
                misleading,keys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage,true,new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(
                new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth,KeyPurposeId.id_kp_clientAuth}));
        var cert=new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate())));
        assertDoesNotThrow(()->DeviceCertificateValidator.validateCandidate(cert));
        byte[] clear;
        try(ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            DataOutputStream output=new DataOutputStream(buffer)) {
            output.writeInt(0x494e5432);
            output.writeLong(stored.getMostSignificantBits());
            output.writeLong(stored.getLeastSignificantBits());
            byte[] pkcs8=keys.getPrivate().getEncoded();
            output.writeInt(pkcs8.length);output.write(pkcs8);
            byte[] x509=cert.getEncoded();
            output.writeInt(x509.length);output.write(x509);
            output.flush();
            clear=buffer.toByteArray();
            Arrays.fill(pkcs8,(byte)0);
        }
        Path file=dir.resolve("spoofed-identity.bin");
        try{
            Files.write(file,Crypt32Util.cryptProtectData(clear));
        }finally{Arrays.fill(clear,(byte)0);}
        assertThrows(IOException.class,()->new DeviceIdentityStore(file).loadOrCreate(),
                "CN prefix/suffix must not be accepted for stored UUID identity");
    }
}
