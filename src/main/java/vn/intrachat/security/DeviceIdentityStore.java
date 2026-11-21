package vn.intrachat.security;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/** Only the local long-term device credential survives an app restart, DPAPI protected. */
public final class DeviceIdentityStore {
    private static final int MAGIC=0x494e5432; // INT2
    private final Path file;
    public DeviceIdentityStore(Path file){this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();}
    public Path path(){return file;}
    public synchronized DeviceIdentity loadOrCreate() throws IOException,GeneralSecurityException {
        if(Files.exists(file))return load();
        DeviceIdentity newIdentity=DeviceCertificateFactory.create(UUID.randomUUID());
        store(newIdentity);
        return newIdentity;
    }
    private DeviceIdentity load()throws IOException,GeneralSecurityException{
        byte[] clear=ProtectedLocalFile.read(file);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(clear))){
            if(in.readInt()!=MAGIC)throw new IOException("Invalid credential store format");
            UUID id=new UUID(in.readLong(),in.readLong());
            byte[] encodedPrivate=bounded(in,8192);
            byte[] encodedCertificate=bounded(in,16384);
            if(in.available()!=0)throw new IOException("Unexpected credential bytes");
            PrivateKey privateKey=KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(encodedPrivate));
            X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(encodedCertificate));
            DeviceCertificateValidator.validateCandidate(certificate);
            if(!certificate.getSubjectX500Principal().getName().equalsIgnoreCase("CN="+id))
                throw new IOException("Credential identity mismatch");
            byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);
            Signature sign=Signature.getInstance("SHA256withECDSA");
            sign.initSign(privateKey);sign.update(challenge);byte[] signed=sign.sign();
            sign.initVerify(certificate.getPublicKey());sign.update(challenge);
            if(!sign.verify(signed))throw new IOException("Credential private/public key mismatch");
            Arrays.fill(encodedPrivate,(byte)0);
            return new DeviceIdentity(id,new KeyPair(certificate.getPublicKey(),privateKey),certificate);
        }finally{Arrays.fill(clear,(byte)0);}
    }
    private void store(DeviceIdentity identity)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream output=new DataOutputStream(bytes)){
            output.writeInt(MAGIC);
            output.writeLong(identity.deviceId().getMostSignificantBits());
            output.writeLong(identity.deviceId().getLeastSignificantBits());
            writeBlob(output,identity.keyPair().getPrivate().getEncoded());
            try {writeBlob(output,identity.certificate().getEncoded());}
            catch(java.security.cert.CertificateEncodingException e){throw new IOException("Cannot encode identity certificate",e);}
        }
        byte[] clear=bytes.toByteArray();
        try{ProtectedLocalFile.write(file,clear);}
        finally{Arrays.fill(clear,(byte)0);}
    }
    private static void writeBlob(DataOutputStream out,byte[] blob)throws IOException{
        out.writeInt(blob.length);out.write(blob);
    }
    private static byte[] bounded(DataInputStream in,int max)throws IOException{
        int size=in.readInt();
        if(size<1||size>max)throw new IOException("Invalid credential component size");
        byte[] blob=new byte[size];in.readFully(blob);return blob;
    }
}
