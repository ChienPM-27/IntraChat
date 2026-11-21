package vn.intrachat.application.file;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import vn.intrachat.domain.file.FilePolicy;

/**
 * Read-only preflight of a human-selected regular source file.
 * This is NOT the reviewed Windows source-handle/TOCTOU protocol for production transfers.
 */
public final class SourceFileReader {
    private SourceFileReader() { }
    public record SelectedSource(Path path,long sizeBytes,Object fileKey,FileTime lastModified) { }
    public static SelectedSource inspect(Path chosen) throws IOException {
        Objects.requireNonNull(chosen,"source");
        BasicFileAttributes attrs=Files.readAttributes(chosen,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || Files.isSymbolicLink(chosen))
            throw new IllegalArgumentException("Source must be a readable regular file");
        long size=FilePolicy.requireSize(attrs.size());
        if (!Files.isReadable(chosen)) throw new IOException("Source is not readable");
        return new SelectedSource(chosen.toAbsolutePath().normalize(),size,attrs.fileKey(),attrs.lastModifiedTime());
    }
    public static boolean sameSelectedFile(SelectedSource a,SelectedSource b) {
        return a!=null && b!=null && a.path().equals(b.path()) &&
            a.sizeBytes()==b.sizeBytes() &&
            Objects.equals(a.fileKey(),b.fileKey()) &&
            Objects.equals(a.lastModified(),b.lastModified());
    }
}
