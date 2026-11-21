package vn.intrachat.domain.history;

/** Scope-local history copy state, independent of M2/Group message delivery/read. */
public record HistorySyncState(State state) {
    public enum State { LOCAL_ONLY, SYNC_PENDING, PARTIAL, SYNCED, ERROR }
    public HistorySyncState {
        if (state == null) throw new NullPointerException("state");
    }
    public static HistorySyncState initial() { return new HistorySyncState(State.LOCAL_ONLY); }
    public HistorySyncState startManual(boolean verifiedReady, boolean userInitiated) {
        if (!verifiedReady || !userInitiated)
            throw new SecurityException("Verified READY and explicit per-conversation Sync required");
        return new HistorySyncState(State.SYNC_PENDING);
    }
    public HistorySyncState finish(boolean durableCopyCommitted, boolean scopedProofMatches) {
        requirePending();
        return new HistorySyncState(durableCopyCommitted && scopedProofMatches ?
                                    State.SYNCED : State.PARTIAL);
    }
    public HistorySyncState cancel() {
        requirePending();
        return new HistorySyncState(State.PARTIAL);
    }
    public HistorySyncState fail() { return new HistorySyncState(State.ERROR); }
    private void requirePending() {
        if (state != State.SYNC_PENDING)
            throw new IllegalStateException("History Sync was not pending");
    }
}
