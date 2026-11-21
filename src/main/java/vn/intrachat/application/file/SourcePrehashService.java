package vn.intrachat.application.file;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.Objects;
import vn.intrachat.domain.file.FilePolicy;
import vn.intrachat.domain.file.FileIntegrityManifest;

/**
 * Bounded-memory SHA-256 source inspection prior to offer.
 * No network offer is emitted here. It does not acquire a Windows stable-handle lock.
 */
public final class SourcePrehashService {
    private SourcePrehashService() { }
    public static final int BUFFER_BYTES = 32 * 1024;

    public static final class SourceSnapshot {
        private final SourceFileReader.SelectedSource selected;
        private final byte[] sha256;
        public SourceSnapshot(SourceFileReader.SelectedSource selected,byte[] sha256) {
            this.selected=Objects.requireNonNull(selected,"selected");
            if (sha256==null||sha256.length!=32)
                throw new IllegalArgumentException("SHA-256 length must be 32 bytes");
            this.sha256=sha256.clone();
        }
        public long sizeBytes(){return selected.sizeBytes();}
        public byte[] sha256(){return sha256.clone();}
        public SourceFileReader.SelectedSource selected(){return selected;}
    }

    public static SourceSnapshot prehash(Path source) throws IOException {
        SourceFileReader.SelectedSource before=SourceFileReader.inspect(source);
        final MessageDigest sha;
        try {sha=MessageDigest.getInstance("SHA-256");}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
        byte[] block=new byte[BUFFER_BYTES];
        long bytes=0L;
        try(InputStream in=Files.newInputStream(source,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            int n;
            while((n=in.read(block))!=-1) {
                if(n==0) continue;
                if(bytes>FilePolicy.MAX_FILE_BYTES-n)
                    throw new IOException("Source expanded past 256 MiB while hashing");
                bytes+=n;
                sha.update(block,0,n);
            }
        }
        SourceFileReader.SelectedSource after=SourceFileReader.inspect(source);
        if(bytes!=before.sizeBytes() || !SourceFileReader.sameSelectedFile(before,after))
            throw new IOException("Source changed during SHA-256 prehash");
        return new SourceSnapshot(before,sha.digest());
    }
}
