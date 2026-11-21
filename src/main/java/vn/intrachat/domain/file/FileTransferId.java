package vn.intrachat.domain.file;

import java.util.Objects;
import java.util.UUID;

/** Fresh per-attempt ID; never reused for an automatic resume. */
public record FileTransferId(UUID value) {
    public FileTransferId {
        Objects.requireNonNull(value, "transferId");
        if (value.equals(new UUID(0L,0L))) throw new IllegalArgumentException("Reserved transferId");
    }
    public static FileTransferId random() { return new FileTransferId(UUID.randomUUID()); }
}
