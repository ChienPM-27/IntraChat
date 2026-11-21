package vn.intrachat.domain.file;
import vn.intrachat.application.file.SourceFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.RandomAccessFile;
import static org.junit.jupiter.api.Assertions.*;
class FileSourceReaderTest {
 @TempDir Path dir;
 @Test void emptyAndRegularAndBoundaries() throws Exception {
   Path empty=Files.createFile(dir.resolve("empty.bin"));
   assertEquals(0,SourceFileReader.inspect(empty).sizeBytes());
   Path one=dir.resolve("one.bin");Files.write(one,new byte[]{7});
   assertEquals(1,SourceFileReader.inspect(one).sizeBytes());
   assertThrows(IllegalArgumentException.class,()->SourceFileReader.inspect(dir));
   Path huge=dir.resolve("oversize.bin");
   try(RandomAccessFile raf=new RandomAccessFile(huge.toFile(),"rw")){raf.setLength(FilePolicy.MAX_FILE_BYTES+1);}
   assertThrows(IllegalArgumentException.class,()->SourceFileReader.inspect(huge));
 }
 @Test void nonexistentSourceFailsClosed(){
   assertThrows(Exception.class,()->SourceFileReader.inspect(dir.resolve("missing")));
 }
}