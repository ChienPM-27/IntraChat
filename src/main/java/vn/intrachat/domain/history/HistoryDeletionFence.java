package vn.intrachat.domain.history;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable local-only tombstone/generation policy, NOT remote deletion.
 * A trusted importer must also verify signed event origin/activation eligibility.
 * The generation is local state and MUST NOT be accepted from a remote sender.
 */
public final class HistoryDeletionFence {
    private static final UUID ZERO = new UUID(0L,0L);
    private final long generation;
    private final Set<UUID> tombstones;

    private HistoryDeletionFence(long generation, Set<UUID> tombstones) {
        if (generation < 1) throw new IllegalArgumentException("Invalid generation");
        this.generation = generation;
        this.tombstones = Set.copyOf(tombstones);
    }

    public static HistoryDeletionFence initial() { return new HistoryDeletionFence(1,Set.of()); }
    public long generation() { return generation; }
    public boolean canImport(UUID id, long trustedLocalGeneration) {
        Objects.requireNonNull(id,"eventId");
        if (ZERO.equals(id)) return false;
        return trustedLocalGeneration == generation && !tombstones.contains(id);
    }
    public HistoryDeletionFence deleteEvent(UUID id) {
        Objects.requireNonNull(id,"eventId");
        if (ZERO.equals(id)) throw new IllegalArgumentException("Reserved eventId");
        Set<UUID> next = new HashSet<>(tombstones);
        next.add(id);
        return new HistoryDeletionFence(generation,next);
    }
    public HistoryDeletionFence deleteConversation() {
        if (generation == Long.MAX_VALUE) throw new IllegalStateException("Deletion generation exhausted");
        return new HistoryDeletionFence(generation + 1,tombstones);
    }
    public boolean isTombstoned(UUID id) { return tombstones.contains(Objects.requireNonNull(id)); }
}
