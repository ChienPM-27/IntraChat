package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupHistoryEligibilityTest {
    private static GroupHistoryPosition p(long epoch,long revision,long sequence) {
        return new GroupHistoryPosition(epoch,revision,sequence);
    }

    @Test void joinIsStrictCutoffAndCurrentOwnerAuthorizationIsRequired() {
        GroupHistoryEligibility d=new GroupHistoryEligibility(UUID.randomUUID(),UUID.randomUUID(),p(1,5,10),null);
        assertFalse(d.canIssue(p(1,4,9),true,true));
        assertFalse(d.canIssue(p(1,5,10),true,true));
        assertTrue(d.canIssue(p(1,5,11),true,true));
        assertTrue(d.canIssue(p(2,1,1),true,true));
        assertFalse(d.canIssue(p(2,1,1),false,true));
        assertFalse(d.canIssue(p(2,1,1),true,false));
    }

    @Test void removedMemberGetsZeroNewDataEvenOldEvents() {
        GroupHistoryEligibility c=new GroupHistoryEligibility(UUID.randomUUID(),UUID.randomUUID(),
            p(1,1,2),p(1,6,18));
        assertFalse(c.canIssue(p(1,2,3),true,true));
        assertFalse(c.canIssue(p(1,7,19),true,true));
        assertThrows(IllegalArgumentException.class,()->new GroupHistoryEligibility(
            UUID.randomUUID(),UUID.randomUUID(),p(1,5,10),p(1,5,9)));
        assertThrows(IllegalArgumentException.class,()->new GroupHistoryPosition(0,1,1));
    }
}
