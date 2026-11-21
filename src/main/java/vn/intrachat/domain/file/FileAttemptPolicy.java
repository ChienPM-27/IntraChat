package vn.intrachat.domain.file;

import java.util.Objects;

/** No-resume per-user attempt state; no retained chunks, offset, network retries, or disk persistence. */
public final class FileAttemptPolicy {
    private FileAttemptPolicy() { }
    public record Attempt(FileTransferId transferId, boolean cancelled) {
        public Attempt {Objects.requireNonNull(transferId);}
        public Attempt cancel(){return new Attempt(transferId,true);}
        public Attempt retry(FileTransferId next,boolean explicitUserAction) {
            Objects.requireNonNull(next,"new transferId");
            if (!cancelled || !explicitUserAction)
                throw new IllegalStateException("Only a cancelled attempt may be retried by user");
            if (transferId.equals(next)) throw new IllegalArgumentException("New retry must have a new ID");
            return new Attempt(next,false);
        }
        public boolean receiverConsented(){return false;}
        public long resumeOffsetBytes(){return 0;}
    }
    public static Attempt start(FileTransferId id){return new Attempt(id,false);}
}
