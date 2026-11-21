package vn.intrachat.application.file;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Necessary final source check. Production send still needs a reviewed stable Windows
 * handle and must hash the exact emitted bytes before issuing FILE_END.
 */
public final class SourceMutationGuard {
    private SourceMutationGuard() { }
    public static boolean unchanged(Path source,SourcePrehashService.SourceSnapshot initial,
                                    long actualSentBytes,byte[] exactEmittedDigest) {
        if (initial==null || exactEmittedDigest==null || exactEmittedDigest.length!=32 ||
            actualSentBytes!=initial.sizeBytes() ||
            !MessageDigest.isEqual(initial.sha256(),exactEmittedDigest))
            return false;
        try {
            SourcePrehashService.SourceSnapshot current=SourcePrehashService.prehash(source);
            return SourceFileReader.sameSelectedFile(initial.selected(),current.selected()) &&
                MessageDigest.isEqual(initial.sha256(),current.sha256());
        } catch (IOException|IllegalArgumentException ex) {
            return false;
        }
    }
}
