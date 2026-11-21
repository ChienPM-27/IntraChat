package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileRetryNoResumeTest {
  @Test void userRetryNeedsNewIdAndConsent(){
    var id=FileTransferId.random();
    var attempt=FileAttemptPolicy.start(id).cancel();
    assertTrue(attempt.cancelled());
    assertThrows(IllegalArgumentException.class,()->attempt.retry(id,true));
    assertThrows(IllegalStateException.class,()->attempt.retry(FileTransferId.random(),false));
    var next=attempt.retry(FileTransferId.random(),true);
    assertFalse(next.cancelled());
    assertNotEquals(id,next.transferId());
    assertFalse(next.receiverConsented());
    assertEquals(0,next.resumeOffsetBytes());
  }
  @Test void noncancelledCannotRetry(){
    var attempt=FileAttemptPolicy.start(FileTransferId.random());
    assertThrows(IllegalStateException.class,()->attempt.retry(FileTransferId.random(),true));
  }
}