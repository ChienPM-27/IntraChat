package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoryConsentAndTombstoneTest {
    @Test void captureIsStrictlyOptInAndOnlyFutureObservedEvents() {
        Instant t=Instant.parse("2026-11-21T09:00:00Z");
        HistoryConsentPolicy off=HistoryConsentPolicy.disabled();
        assertFalse(off.mayCapture(t.plusSeconds(1)));
        HistoryConsentPolicy on=off.enable(t);
        assertFalse(on.mayCapture(t.minusNanos(1)));
        assertTrue(on.mayCapture(t));
        assertTrue(on.mayCapture(t.plusSeconds(1)));
        assertFalse(on.disable().mayCapture(t.plusSeconds(2)));
        assertFalse(off.mayCapture(t.plusSeconds(2)));
    }

    @Test void tombstoneAndConversationDeletionFenceAreImmutableAndCannotResurrectOldGeneration() {
        UUID deleted=UUID.randomUUID(),future=UUID.randomUUID();
        HistoryDeletionFence original=HistoryDeletionFence.initial();
        assertTrue(original.canImport(deleted,original.generation()));
        HistoryDeletionFence after=original.deleteEvent(deleted);
        assertTrue(original.canImport(deleted,original.generation()));
        assertFalse(after.canImport(deleted,after.generation()));
        assertTrue(after.canImport(future,after.generation()));
        HistoryDeletionFence cleared=after.deleteConversation();
        assertFalse(cleared.canImport(future,after.generation()));
        assertFalse(cleared.canImport(deleted,cleared.generation()));
        assertTrue(cleared.canImport(future,cleared.generation()));
    }
}
