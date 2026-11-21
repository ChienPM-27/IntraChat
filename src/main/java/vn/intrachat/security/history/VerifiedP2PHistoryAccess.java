package vn.intrachat.security.history;

import java.util.UUID;
import vn.intrachat.domain.history.HistoryConversationKey;
import vn.intrachat.domain.history.HistoryScope;

/**
 * Exact pair/consent gate for P2P History summaries and deltas.
 *
 * SECURITY BOUNDARY: the caller MUST populate peer IDs and SPKI fingerprints
 * from the mutually pinned/human-approved M2 TLS session, never from self-claimed
 * JSON/wire data. This pure policy is NOT proof of TLS verification or consent
 * by itself and MUST NOT be used as the sole enforcement in a live dispatcher.
 */
public final class VerifiedP2PHistoryAccess {
    private VerifiedP2PHistoryAccess() { }

    public static boolean canDisclose(HistoryScope scope,
                                       UUID localId, byte[] localVerifiedSpkiSha256,
                                       UUID remoteId, byte[] remoteVerifiedSpkiSha256,
                                       boolean m2PairReady,
                                       boolean historyCapabilityConfirmed,
                                       boolean localHistoryOptIn,
                                       boolean remoteHistoryPermission) {
        if (scope == null || scope.kind() != HistoryScope.Kind.P2P ||
            !m2PairReady || !historyCapabilityConfirmed ||
            !localHistoryOptIn || !remoteHistoryPermission)
            return false;
        try {
            return scope.equals(HistoryConversationKey.forVerifiedPeers(
                localId,localVerifiedSpkiSha256,remoteId,remoteVerifiedSpkiSha256));
        } catch (IllegalArgumentException | NullPointerException invalidOrMissingIdentity) {
            return false; // generic denial, never reveal if another conversation exists
        }
    }
}
