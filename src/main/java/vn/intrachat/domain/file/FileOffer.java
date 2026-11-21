package vn.intrachat.domain.file;

import java.util.Objects;

/** Bounded immutable metadata, never an authority to save a pathname. */
public final class FileOffer {
    private final FileTransferId transferId;
    private final VerifiedFileScope scope;
    private final String basename;
    private final long sizeBytes;
    private final byte[] sha256;

    public FileOffer(FileTransferId transferId, VerifiedFileScope scope,
                     String basename, long sizeBytes, byte[] sha256) {
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.basename = FileDisplayBasename.requireSafe(basename);
        this.sizeBytes = FilePolicy.requireSize(sizeBytes);
        if (sha256 == null || sha256.length != 32)
            throw new IllegalArgumentException("SHA-256 must be exactly 32 bytes");
        this.sha256 = sha256.clone();
    }
    public FileTransferId transferId() { return transferId; }
    public VerifiedFileScope scope() { return scope; }
    public String basename() { return basename; }
    public long sizeBytes() { return sizeBytes; }
    public byte[] sha256() { return sha256.clone(); }
}
