package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileNoHistoryReceiptTest {
  @Test void verifiedFileCommitIsIndependentOfChatRead(){
    var m=FileIntegrityManifest.fromBytes(new byte[]{9,8,7});
    var result=FileCopyOutcome.fromReceiverCommit(true,m.sizeBytes(),m.sha256(),m.sizeBytes(),m.sha256());
    assertTrue(result.copyCompleted());
    assertFalse(result.impliesChatDeliveryReceipt());
    assertFalse(result.impliesChatReadReceipt());
    assertFalse(result.isHistoryEvent());
  }
  @Test void lostOrTamperedCommitNeverCompletesCopy(){
    var m=FileIntegrityManifest.fromBytes(new byte[]{1});
    assertFalse(FileCopyOutcome.fromReceiverCommit(false,1,m.sha256(),1,m.sha256()).copyCompleted());
    assertFalse(FileCopyOutcome.fromReceiverCommit(true,1,m.sha256(),1,new byte[32]).copyCompleted());
    assertFalse(FileCopyOutcome.fromReceiverCommit(true,1,m.sha256(),0,m.sha256()).copyCompleted());
  }
}