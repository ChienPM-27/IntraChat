package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupRecipientStatusTest {
    @Test void ownerAcceptanceNeverEqualsRecipientAckOrRead() {
        UUID b = UUID.randomUUID(), c = UUID.randomUUID(), outsider = UUID.randomUUID();
        GroupRecipientStatus ledger = new GroupRecipientStatus(Set.of(b, c));
        ledger = ledger.ownerAccepted();
        assertTrue(ledger.isOwnerAccepted());
        assertEquals(0, ledger.deliveredCount());
        assertEquals(0, ledger.readCount());
        ledger = ledger.recipientAck(b);
        assertEquals(1, ledger.deliveredCount());
        assertEquals(GroupRecipientStatus.State.PENDING, ledger.statusOf(c));
        GroupRecipientStatus immutableLedger = ledger;
        assertThrows(SecurityException.class, () -> immutableLedger.recipientAck(outsider));
        assertEquals(2, ledger.recipients().size());
    }

    @Test void viewerReadCanPrecedeAckOnlyForThatRecipientAndNeverRegresses() {
        UUID b = UUID.randomUUID(), c = UUID.randomUUID();
        GroupRecipientStatus state = new GroupRecipientStatus(Set.of(b, c));
        state = state.recipientRead(c);
        assertEquals(GroupRecipientStatus.State.VIEWED, state.statusOf(c));
        assertEquals(GroupRecipientStatus.State.PENDING, state.statusOf(b));
        assertEquals(1, state.deliveredCount());
        assertEquals(1, state.readCount());
        state = state.recipientAck(c);
        assertEquals(GroupRecipientStatus.State.VIEWED, state.statusOf(c));
        state = state.recipientOffline(b);
        assertEquals(GroupRecipientStatus.State.OFFLINE, state.statusOf(b));
        state = state.recipientAck(b);
        assertEquals(2, state.deliveredCount());
        assertEquals(1, state.readCount());
        state = state.recipientOffline(c);
        assertEquals(GroupRecipientStatus.State.VIEWED, state.statusOf(c));
    }

    @Test void recipientsSetIsImmutableAndNoForeignRecipientCanBeInserted() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        HashSet<UUID> original = new HashSet<>(Set.of(first, second));
        GroupRecipientStatus status = new GroupRecipientStatus(original);
        original.remove(second);
        assertEquals(2, status.recipients().size());
        assertThrows(UnsupportedOperationException.class, () -> status.recipients().clear());
        assertThrows(SecurityException.class, () -> status.recipientRead(UUID.randomUUID()));
    }
}
