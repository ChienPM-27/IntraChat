package vn.intrachat.domain.file;
import vn.intrachat.application.file.SourcePrehashService;
import vn.intrachat.application.file.SourceMutationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;
class SourceMutationStreamingTest {
 @TempDir Path dir;
 @Test void sameLengthMutationRejectedEvenIfMtimeRestored() throws Exception {
   Path f=dir.resolve("data");Files.write(f,new byte[]{1,2,3,4});
   var before=SourcePrehashService.prehash(f);
   assertTrue(SourceMutationGuard.unchanged(f,before,4,before.sha256()));
   var time=Files.getLastModifiedTime(f);
   Files.write(f,new byte[]{9,8,7,6});
   Files.setLastModifiedTime(f,time);
   assertFalse(SourceMutationGuard.unchanged(f,before,4,before.sha256()));
 }
 @Test void truncatedAndDifferentSentDigestRejected() throws Exception {
   Path f=dir.resolve("source");Files.write(f,new byte[]{1,2});
   var before=SourcePrehashService.prehash(f);
   assertFalse(SourceMutationGuard.unchanged(f,before,2,new byte[32]));
   Files.write(f,new byte[]{1});
   assertFalse(SourceMutationGuard.unchanged(f,before,2,before.sha256()));
 }
}