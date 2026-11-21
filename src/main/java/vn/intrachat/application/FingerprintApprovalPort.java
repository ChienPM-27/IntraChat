package vn.intrachat.application;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Explicit person comparison of matching codes via an independent channel. */
@FunctionalInterface
public interface FingerprintApprovalPort {
    CompletionStage<Boolean> requestComparison(Comparison comparison);
    record Comparison(UUID remoteId,String remoteDisplayName,String endpoint,
        String sharedCode,String ownFullFingerprint,String remoteFullFingerprint) { }
}
