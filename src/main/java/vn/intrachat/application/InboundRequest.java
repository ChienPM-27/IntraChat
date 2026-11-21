package vn.intrachat.application;

import vn.intrachat.domain.PeerIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The observed IP/port and a self-reported, UNVERIFIED peer claim. */
public record InboundRequest(UUID requestId, String remoteEndpoint,
                             PeerIdentity claimedPeer, Instant deadline) {
    public InboundRequest {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(remoteEndpoint);
        Objects.requireNonNull(claimedPeer);
        Objects.requireNonNull(deadline);
    }
}
