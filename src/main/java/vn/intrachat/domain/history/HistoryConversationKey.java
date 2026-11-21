package vn.intrachat.domain.history;

import java.util.UUID;
import vn.intrachat.domain.P2PConversation;

/**
 * Stable canonical key for exactly two independently verified devices.
 * Computes identity only; authorization and PIN verification are separate security ports.
 */
public final class HistoryConversationKey {
    private HistoryConversationKey() { }

    public static HistoryScope forVerifiedPeers(UUID first, byte[] firstSpkiSha256,
                                                 UUID second, byte[] secondSpkiSha256) {
        if (first == null || second == null) throw new NullPointerException("Device ID required");
        if (first.equals(new UUID(0L,0L)) || second.equals(new UUID(0L,0L)))
            throw new IllegalArgumentException("Reserved device ID");
        return HistoryScope.p2p(P2PConversation.idFor(first,firstSpkiSha256,
                                                     second,secondSpkiSha256));
    }
}
