package vn.intrachat.domain;

import java.util.Objects;
import java.util.UUID;

/** Transport session lifecycle, deliberately not an authentication or message-delivery state. */
public final class ConnectionSession {
    private final UUID sessionId;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    private boolean approvalGranted;
    private boolean awaitingApproval;
    private boolean terminated;

    public ConnectionSession(UUID sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public UUID sessionId() { return sessionId; }
    public synchronized ConnectionState state() { return state; }
    public synchronized boolean approved() { return approvalGranted; }

    public synchronized void beginConnecting() {
        if (terminated || state != ConnectionState.DISCONNECTED) throw new IllegalStateException("Session is not new");
        state = ConnectionState.CONNECTING;
    }

    public synchronized void waitingApproval() {
        if (terminated || state != ConnectionState.CONNECTING) throw new IllegalStateException("Not connecting");
        awaitingApproval = true;
    }

    public synchronized void approve() {
        if (terminated || !awaitingApproval || state != ConnectionState.CONNECTING) {
            throw new IllegalStateException("Cannot approve this session");
        }
        approvalGranted = true;
        awaitingApproval = false;
    }

    public synchronized void markReady() {
        if (terminated || !approvalGranted || state != ConnectionState.CONNECTING) {
            throw new IllegalStateException("Session not approved and negotiated");
        }
        state = ConnectionState.CONNECTED;
    }

    public synchronized void reject() {
        if (terminated || !awaitingApproval) throw new IllegalStateException("No pending approval");
        awaitingApproval = false;
        state = ConnectionState.FAILED;
        terminated = true;
    }

    public synchronized void fail() {
        terminated = true;
        state = ConnectionState.FAILED;
    }

    public synchronized void close() {
        terminated = true;
        state = ConnectionState.DISCONNECTED;
    }
}
