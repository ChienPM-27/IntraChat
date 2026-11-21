package vn.intrachat.domain.history;

/** Local durable history state, not a remote application ACK or READ receipt. */
public enum HistoryLocalState {
    UNSAVED, SAVED;
    public HistoryLocalState afterDurableCommit(boolean commitSucceeded) {
        return commitSucceeded ? SAVED : this;
    }
}
