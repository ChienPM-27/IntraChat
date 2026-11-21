package vn.intrachat.domain.file;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroupFileEligibilityTest {
  @Test void currentSignedOwnerAndMemberAndJoinWindowRequired(){
    assertTrue(GroupFileEligibility.canIssue(true,true,true,2,2,5,5,12,10,null,13));
    assertFalse(GroupFileEligibility.canIssue(false,true,true,2,2,5,5,12,10,null,13));
    assertFalse(GroupFileEligibility.canIssue(true,false,true,2,2,5,5,12,10,null,13));
    assertFalse(GroupFileEligibility.canIssue(true,true,true,3,2,5,5,12,10,null,13));
    assertFalse(GroupFileEligibility.canIssue(true,true,true,2,2,6,5,12,10,null,13));
  }
  @Test void lateJoinAndRemovalNeverIssueNewChunk(){
    assertFalse(GroupFileEligibility.canIssue(true,true,true,2,2,5,5,12,13,null,13));
    assertFalse(GroupFileEligibility.canIssue(true,true,true,2,2,5,5,12,10,13L,13));
    assertFalse(GroupFileEligibility.canIssue(true,true,false,2,2,5,5,12,10,null,13));
    assertFalse(GroupFileEligibility.canIssue(true,true,true,2,2,5,5,12,10,12L,13));
  }
}