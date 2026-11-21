package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileProgressSnapshotTest {
  @Test void countersBoundedAndIndependent(){
    var p=new FileProgressSnapshot(100,100,80,0,0,FileTransferStatusReducer.Stage.WAITING_RECEIVER_COMMIT);
    assertEquals(100,p.bytesSent());
    assertEquals(80,p.bytesAccepted());
    assertFalse(p.copyCompleted());
    assertThrows(IllegalArgumentException.class,()->new FileProgressSnapshot(100,101,0,0,0,FileTransferStatusReducer.Stage.STREAMING));
    assertThrows(IllegalArgumentException.class,()->new FileProgressSnapshot(100,-1,0,0,0,FileTransferStatusReducer.Stage.STREAMING));
    assertThrows(IllegalArgumentException.class,()->new FileProgressSnapshot(100,1,2,0,0,FileTransferStatusReducer.Stage.STREAMING));
  }
  @Test void evenEmptyFileNeedsCommitOutcome(){
    assertFalse(new FileProgressSnapshot(0,0,0,0,0,FileTransferStatusReducer.Stage.WAITING_RECEIVER_COMMIT).copyCompleted());
    assertTrue(new FileProgressSnapshot(0,0,0,0,0,FileTransferStatusReducer.Stage.COMPLETED).copyCompleted());
  }
}