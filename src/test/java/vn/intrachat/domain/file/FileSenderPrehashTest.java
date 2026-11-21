package vn.intrachat.domain.file;
import vn.intrachat.application.file.SourcePrehashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FileSenderPrehashTest {
 @TempDir Path dir;
 @Test void emptyAndBufferLargeDigest() throws Exception {
   Path empty=Files.createFile(dir.resolve("zero"));
   var first=SourcePrehashService.prehash(empty);
   assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",HexFormat.of().formatHex(first.sha256()));
   byte[] bytes=new byte[1024*1024+37];new Random(44).nextBytes(bytes);
   Path large=dir.resolve("large");Files.write(large,bytes);
   var second=SourcePrehashService.prehash(large);
   assertEquals(bytes.length,second.sizeBytes());
   assertArrayEquals(FileIntegrityManifest.fromBytes(bytes).sha256(),second.sha256());
 }
}