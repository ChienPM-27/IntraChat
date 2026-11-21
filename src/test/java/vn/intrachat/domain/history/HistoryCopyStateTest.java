package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryCopyStateTest {
    @Test void localDurableCommitAndRemoteCopyAreSeparateFromRead() {
        HistoryLocalState local=HistoryLocalState.UNSAVED;
        assertEquals(HistoryLocalState.UNSAVED,local);
        assertEquals(HistoryLocalState.SAVED,local.afterDurableCommit(true));
        assertEquals(HistoryLocalState.UNSAVED,local.afterDurableCommit(false));
    }

    @Test void manualVerifiedSyncMustFinishDurablyBeforeSynced() {
        HistorySyncState sync=HistorySyncState.initial();
        assertEquals(HistorySyncState.State.LOCAL_ONLY,sync.state());
        assertThrows(SecurityException.class,()->sync.startManual(false,true));
        assertThrows(SecurityException.class,()->sync.startManual(true,false));
        HistorySyncState pending=sync.startManual(true,true);
        assertEquals(HistorySyncState.State.SYNC_PENDING,pending.state());
        assertEquals(HistorySyncState.State.PARTIAL,pending.finish(false,true).state());
        assertEquals(HistorySyncState.State.PARTIAL,pending.finish(true,false).state());
        assertEquals(HistorySyncState.State.SYNCED,pending.finish(true,true).state());
        assertEquals(HistorySyncState.State.PARTIAL,pending.cancel().state());
        assertEquals(HistorySyncState.State.ERROR,pending.fail().state());
        assertEquals(HistorySyncState.State.SYNC_PENDING,pending.cancel().startManual(true,true).state());
    }
}
