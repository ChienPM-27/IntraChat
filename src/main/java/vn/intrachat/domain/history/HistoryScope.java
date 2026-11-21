package vn.intrachat.domain.history;

import java.util.Objects;
import java.util.UUID;

/** A typed, nonsecret conversation key: P2P and GROUP never share a namespace. */
public record HistoryScope(Kind kind, UUID scopeId) {
    public enum Kind { P2P, GROUP }
    private static final UUID ZERO = new UUID(0L,0L);

    public HistoryScope {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scopeId, "scopeId");
        if (ZERO.equals(scopeId)) throw new IllegalArgumentException("Reserved History scope ID");
    }
    public static HistoryScope p2p(UUID id) { return new HistoryScope(Kind.P2P,id); }
    public static HistoryScope group(UUID id) { return new HistoryScope(Kind.GROUP,id); }
}
