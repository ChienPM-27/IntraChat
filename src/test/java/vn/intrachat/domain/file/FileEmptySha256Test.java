package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
class FileEmptySha256Test {
  @Test void emptyFileDigestAndZeroLengthVerify(){
    var empty=FileIntegrityManifest.fromBytes(new byte[0]);
    assertEquals(0,empty.sizeBytes());
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",java.util.HexFormat.of().formatHex(empty.sha256()));
    assertTrue(empty.matches(0,empty.sha256()));
    assertFalse(empty.matches(1,empty.sha256()));
    assertFalse(empty.matches(0,new byte[32]));
  }
  @Test void digestDefensivelyCopiedAndValidated(){
    var manifest=FileIntegrityManifest.fromBytes(new byte[]{1,2,3});
    byte[] mutable=manifest.sha256(); mutable[0]^=1;
    assertFalse(MessageDigest.isEqual(mutable,manifest.sha256()));
    assertThrows(IllegalArgumentException.class,()->new FileIntegrityManifest(0,new byte[31]));
  }
}