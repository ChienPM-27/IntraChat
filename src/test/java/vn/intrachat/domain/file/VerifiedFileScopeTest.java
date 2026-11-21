package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class VerifiedFileScopeTest {
  static final UUID A=UUID.fromString("00000000-0000-0000-0000-000000000001"),B=UUID.fromString("00000000-0000-0000-0000-000000000002"),C=UUID.fromString("00000000-0000-0000-0000-000000000003");
  static byte[] key(int n){byte[] b=new byte[32]; b[0]=(byte)n; return b;}
  @Test void pairOrderAndExactPin() {
    var ab=VerifiedFileScope.p2p(A,key(1),B,key(2));
    var ba=VerifiedFileScope.p2p(B,key(2),A,key(1));
    assertEquals(ab.conversationId(),ba.conversationId());
    assertTrue(ab.allows(A,key(1)));
    assertTrue(ab.allows(B,key(2)));
    assertFalse(ab.allows(A,key(9)));
    assertFalse(ab.allows(C,key(3)));
  }
  @Test void invalidAndMutatedFingerprintCannotAuthorize(){
    assertThrows(IllegalArgumentException.class,()->VerifiedFileScope.p2p(A,key(1),A,key(2)));
    assertThrows(IllegalArgumentException.class,()->VerifiedFileScope.p2p(new UUID(0,0),key(1),B,key(2)));
    assertThrows(IllegalArgumentException.class,()->VerifiedFileScope.p2p(A,new byte[3],B,key(2)));
    byte[] mutable=key(1);
    var ab=VerifiedFileScope.p2p(A,mutable,B,key(2));
    mutable[0]=99;
    assertTrue(ab.allows(A,key(1)));
    assertFalse(ab.allows(A,mutable));
  }
}