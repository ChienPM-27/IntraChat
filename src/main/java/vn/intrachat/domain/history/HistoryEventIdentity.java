package vn.intrachat.domain.history;

import java.security.MessageDigest;
import java.util.Objects;

/** Immutable scoped event deduplication; never overwrite a conflicting source. */
public final class HistoryEventIdentity {
    private HistoryEventIdentity() { }

    public static HistoryConflictDecision classify(HistoryEvent existing, HistoryEvent offered) {
        Objects.requireNonNull(offered, "offered");
        if (existing == null || !existing.scope().equals(offered.scope()) ||
            !existing.eventId().equals(offered.eventId()))
            return HistoryConflictDecision.INSERT;
        boolean same = existing.originalMessageId().equals(offered.originalMessageId()) &&
            existing.originDeviceId().equals(offered.originDeviceId()) &&
            existing.sourceSentAtUtc().equals(offered.sourceSentAtUtc()) &&
            existing.sourceProtocolVersion() == offered.sourceProtocolVersion() &&
            existing.content().kind() == offered.content().kind() &&
            MessageDigest.isEqual(existing.contentSha256(),offered.contentSha256()) &&
            MessageDigest.isEqual(existing.signerSpkiSha256(),offered.signerSpkiSha256()) &&
            MessageDigest.isEqual(existing.originSeal(),offered.originSeal());
        return same ? HistoryConflictDecision.DUPLICATE : HistoryConflictDecision.QUARANTINE;
    }
}
