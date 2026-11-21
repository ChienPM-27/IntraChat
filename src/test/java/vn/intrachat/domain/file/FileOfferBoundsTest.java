package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class FileOfferBoundsTest {
  @Test void zeroAndMaxAreValidAndAboveMaxRejected(){
    assertEquals(268435456L,FilePolicy.MAX_FILE_BYTES);
    assertEquals(0,FilePolicy.requireSize(0));
    assertEquals(268435456L,FilePolicy.requireSize(268435456L));
    assertThrows(IllegalArgumentException.class,()->FilePolicy.requireSize(-1));
    assertThrows(IllegalArgumentException.class,()->FilePolicy.requireSize(268435457L));
    assertThrows(IllegalArgumentException.class,()->FilePolicy.requireSize(Long.MAX_VALUE));
  }
  @Test void offerValidatesAndDefensivelyCopiesSha(){
    var scope=VerifiedFileScope.p2p(VerifiedFileScopeTest.A,VerifiedFileScopeTest.key(1),VerifiedFileScopeTest.B,VerifiedFileScopeTest.key(2));
    byte[] hash=new byte[32]; hash[0]=21;
    var offer=new FileOffer(FileTransferId.random(),scope,"data.bin",0,hash);
    hash[0]=1;
    assertEquals(21,offer.sha256()[0]);
    byte[] back=offer.sha256();back[0]=3;assertEquals(21,offer.sha256()[0]);
    assertThrows(IllegalArgumentException.class,()->new FileOffer(FileTransferId.random(),scope,"data.bin",268435457L,new byte[32]));
    assertThrows(IllegalArgumentException.class,()->new FileOffer(FileTransferId.random(),scope,"data.bin",0,new byte[31]));
    assertThrows(IllegalArgumentException.class,()->new FileTransferId(new UUID(0,0)));
  }
}