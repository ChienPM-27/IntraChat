package vn.intrachat.domain.file;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/** A raw-byte length and SHA-256 pair; streaming I/O and source-mutation checks live in other ports. */
public final class FileIntegrityManifest {
    private final long sizeBytes;
    private final byte[] sha256;
    public FileIntegrityManifest(long sizeBytes, byte[] sha256) {
        this.sizeBytes=FilePolicy.requireSize(sizeBytes);
        if (sha256==null || sha256.length!=32)
            throw new IllegalArgumentException("Expected 32-byte SHA-256");
        this.sha256=sha256.clone();
    }
    /** Only for in-memory small test fixtures; production File streaming must use bounded MessageDigest updates. */
    public static FileIntegrityManifest fromBytes(byte[] data) {
        Objects.requireNonNull(data,"data");
        FilePolicy.requireSize(data.length);
        try {
            return new FileIntegrityManifest(data.length,MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    public long sizeBytes(){return sizeBytes;}
    public byte[] sha256(){return sha256.clone();}
    public boolean matches(long actualSize, byte[] actualHash) {
        return sizeBytes==actualSize && actualHash!=null &&
               MessageDigest.isEqual(sha256,actualHash);
    }
}
