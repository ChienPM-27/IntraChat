package vn.intrachat.transport;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Pure-clock liveness policy for M1 unauthenticated diagnostic TCP sessions.
 * The caller runs tick() every ten seconds on a bounded scheduler, sends a
 * synthetic PING for PING, and closes the socket on CLOSE. No auto reconnect.
 *
 * A decoded, identity-bound inbound control frame updates the liveness clock;
 * an arbitrary TCP byte must never count as a valid PONG or keepalive.
 */
final class DiagnosticHeartbeat implements AutoCloseable {
    enum Decision { PING, CLOSE, NONE }
    private static final long SILENCE_LIMIT_NANOS = TimeUnit.SECONDS.toNanos(30);
    private final LongSupplier clock;
    private long lastInbound;
    private boolean closed;

    DiagnosticHeartbeat(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lastInbound = clock.getAsLong();
    }

    synchronized Decision tick() {
        if (closed) return Decision.NONE;
        if (clock.getAsLong() - lastInbound >= SILENCE_LIMIT_NANOS) {
            closed = true;
            return Decision.CLOSE;
        }
        return Decision.PING;
    }

    synchronized void recordInbound() {
        if (!closed) lastInbound = clock.getAsLong();
    }

    synchronized boolean silenceExpired() {
        return !closed && clock.getAsLong() - lastInbound >= SILENCE_LIMIT_NANOS;
    }

    synchronized boolean isClosed() { return closed; }

    @Override public synchronized void close() { closed = true; }
}
