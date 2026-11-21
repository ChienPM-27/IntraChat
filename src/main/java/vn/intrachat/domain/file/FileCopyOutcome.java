package vn.intrachat.domain.file;

import java.security.MessageDigest;

/** FILE_COMMIT_OK denotes a copied/verified file, NEVER an app delivery/read receipt.
 * Authentication must be performed by a trusted M2 port, not a self-claimed wire boolean.
 */
public final class FileCopyOutcome {
    private final boolean copyCompleted;
    private FileCopyOutcome(boolean copyCompleted){this.copyCompleted=copyCompleted;}
    public static FileCopyOutcome fromReceiverCommit(boolean independentlyAuthenticated,
                                                      long declaredSize,byte[] expectedSha256,
                                                      long committedSize,byte[] committedSha256) {
        boolean verified=independentlyAuthenticated && declaredSize>=0 &&
            declaredSize<=FilePolicy.MAX_FILE_BYTES && declaredSize==committedSize &&
            expectedSha256!=null && committedSha256!=null &&
            expectedSha256.length==32 && committedSha256.length==32 &&
            MessageDigest.isEqual(expectedSha256,committedSha256);
        return new FileCopyOutcome(verified);
    }
    public boolean copyCompleted(){return copyCompleted;}
    public boolean impliesChatDeliveryReceipt(){return false;}
    public boolean impliesChatReadReceipt(){return false;}
    public boolean isHistoryEvent(){return false;}
}
