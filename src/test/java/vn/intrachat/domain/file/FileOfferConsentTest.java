package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileOfferConsentTest {
  @Test void downloadsPermissionIsDistinctFromEachOfferAcceptance(){
    var id=FileTransferId.random();
    var c=new FileOfferConsent(id,100,200);
    assertFalse(c.isAccepted());
    assertThrows(IllegalStateException.class,()->c.accept(id,false,120));
    assertFalse(c.isAccepted());
    c.accept(id,true,120);
    assertTrue(c.isAccepted());
    assertThrows(IllegalStateException.class,()->c.accept(id,true,121));
  }
  @Test void rejectTimeoutAndWrongOfferFailClosed(){
    var id=FileTransferId.random();
    var c=new FileOfferConsent(id,100,200);
    assertThrows(SecurityException.class,()->c.accept(FileTransferId.random(),true,120));
    assertThrows(IllegalStateException.class,()->c.accept(id,true,200));
    c.reject(id,150);
    assertFalse(c.isAccepted());
    assertThrows(IllegalStateException.class,()->c.accept(id,true,160));
  }
}