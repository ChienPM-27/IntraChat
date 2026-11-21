package vn.intrachat.domain.file;

import java.util.Objects;

/** RAM-only byte counters. 100% bytes sent does not mean durable remote copy. */
public record FileProgressSnapshot(long totalBytes,long bytesSent,long bytesAccepted,
                                   long bytesVerified,long bytesCommitted,
                                   FileTransferStatusReducer.Stage stage) {
    public FileProgressSnapshot {
        FilePolicy.requireSize(totalBytes);
        Objects.requireNonNull(stage,"stage");
        if (bytesSent<0 || bytesAccepted<0 || bytesVerified<0 || bytesCommitted<0 ||
            bytesSent>totalBytes || bytesAccepted>bytesSent ||
            bytesVerified>bytesAccepted || bytesCommitted>bytesVerified)
            throw new IllegalArgumentException("Nonmonotonic or invalid byte counters");
        if (stage==FileTransferStatusReducer.Stage.COMPLETED && bytesCommitted!=totalBytes)
            throw new IllegalArgumentException("Cannot complete without committed bytes");
    }
    public boolean copyCompleted() {
        return stage==FileTransferStatusReducer.Stage.COMPLETED && bytesCommitted==totalBytes;
    }
}
