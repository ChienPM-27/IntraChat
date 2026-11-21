package vn.intrachat.domain.history;

import java.time.Instant;
import java.util.Objects;

/**
 * Pure local consent gate. Observed-at time is from the local capture point,
 * NOT a peer-supplied sourceSentAt timestamp.
 */
public record HistoryConsentPolicy(State state, Instant activatedAt) {
    public enum State { DISABLED, LOCAL_ONLY }

    public HistoryConsentPolicy {
        Objects.requireNonNull(state, "state");
        if (state == State.LOCAL_ONLY && activatedAt == null)
            throw new IllegalArgumentException("Opt-in activation timestamp required");
        if (state == State.DISABLED && activatedAt != null)
            throw new IllegalArgumentException("Disabled state cannot capture");
    }

    public static HistoryConsentPolicy disabled() { return new HistoryConsentPolicy(State.DISABLED,null); }
    public HistoryConsentPolicy enable(Instant localObservedAt) {
        Objects.requireNonNull(localObservedAt,"localObservedAt");
        if (state == State.LOCAL_ONLY) return this;
        return new HistoryConsentPolicy(State.LOCAL_ONLY,localObservedAt);
    }
    public HistoryConsentPolicy disable() { return disabled(); }
    public boolean mayCapture(Instant localObservedAt) {
        Objects.requireNonNull(localObservedAt,"localObservedAt");
        return state == State.LOCAL_ONLY && !localObservedAt.isBefore(activatedAt);
    }
}
