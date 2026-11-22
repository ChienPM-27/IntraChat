package vn.intrachat.application;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Diagnostic-only M1 application facade. This API intentionally cannot accept
 * user chat text, groups, file bytes or history synchronization. M1 TCP
 * identities are UNVERIFIED and transport is PLAINTEXT by design.
 */
public final class DiagnosticService {
    private final NetworkCoordinator network;

    public DiagnosticService(NetworkCoordinator network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    /** Synthetic DIAGNOSTIC_PING/PONG only after human acceptance and HELLO_READY. */
    public CompletableFuture<Duration> ping(UUID remoteId) {
        Objects.requireNonNull(remoteId, "remoteId");
        return network.ping(remoteId);
    }
}
