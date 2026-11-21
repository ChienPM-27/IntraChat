package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileDisplayBasenameTest {
  @Test void preservesSafeUnicodeButBlocksPathNames(){
    assertEquals("ảnh đẹp.txt",FileDisplayBasename.requireSafe("ảnh đẹp.txt"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("../a.txt"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("..\\evil.txt"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("C:\\temp\\a.exe"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("a.txt:secret"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("CON.txt"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("name. "));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("abc\u202Eexe"));
  }
  @Test void rejectsEmptyControlAndOversizedNames(){
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe(""));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe(".."));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("a\nb"));
    assertThrows(IllegalArgumentException.class,()->FileDisplayBasename.requireSafe("a".repeat(256)));
  }
}