package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileTransferStateTest {
  @Test void senderCannotCompleteFromSentOnly(){
    var s=FileTransferStatusReducer.Stage.SELECTED;
    s=FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.PREHASHING);
    s=FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.OFFERING);
    s=FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.AWAITING_ACCEPT);
    s=FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.STREAMING);
    assertThrows(IllegalStateException.class,()->FileTransferStatusReducer.advance(FileTransferStatusReducer.Stage.STREAMING,FileTransferStatusReducer.Stage.COMPLETED));
    s=FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.WAITING_RECEIVER_COMMIT);
    assertEquals(FileTransferStatusReducer.Stage.COMPLETED,FileTransferStatusReducer.advance(s,FileTransferStatusReducer.Stage.COMPLETED));
  }
  @Test void terminalCannotResurrect(){
    var end=FileTransferStatusReducer.advance(FileTransferStatusReducer.Stage.STREAMING,FileTransferStatusReducer.Stage.CANCELLED);
    assertEquals(end,FileTransferStatusReducer.advance(end,end));
    assertThrows(IllegalStateException.class,()->FileTransferStatusReducer.advance(end,FileTransferStatusReducer.Stage.COMPLETED));
    assertThrows(IllegalStateException.class,()->FileTransferStatusReducer.advance(FileTransferStatusReducer.Stage.REJECTED,FileTransferStatusReducer.Stage.STREAMING));
  }
}