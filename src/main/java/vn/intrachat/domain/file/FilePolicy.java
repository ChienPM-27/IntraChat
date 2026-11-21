package vn.intrachat.domain.file;

/** Confirmed CL-06-01 A. Buffer/chunk caps are separate, still pending technical approval. */
public final class FilePolicy {
    public static final long MAX_FILE_BYTES = 256L * 1024L * 1024L;
    private FilePolicy() { }
    public static long requireSize(long bytes) {
        if (bytes < 0 || bytes > MAX_FILE_BYTES)
            throw new IllegalArgumentException("File size outside 0..256 MiB policy");
        return bytes;
    }
}
