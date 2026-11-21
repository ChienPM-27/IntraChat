package vn.intrachat.transport;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Bounded operational session/pending candidate registry.
 * The UUID is a self-declared peer key; reserving does NOT authenticate a peer.
 */
public final class SessionRegistry implements AutoCloseable {
    private final Semaphore pending = new Semaphore(NetworkConfig.MAX_PENDING_APPROVALS);
    private final Set<UUID> reservedPeers = new HashSet<>();
    private volatile boolean closed;

    public boolean tryReservePending() {
        return !closed && pending.tryAcquire();
    }
    public void releasePending() {
        pending.release();
    }
    public synchronized boolean reservePeer(UUID claimedDeviceId) {
        if (closed || claimedDeviceId == null || reservedPeers.contains(claimedDeviceId)
                || reservedPeers.size() >= NetworkConfig.MAX_ACTIVE_SESSIONS) return false;
        return reservedPeers.add(claimedDeviceId);
    }
    public synchronized void releasePeer(UUID claimedDeviceId) {
        reservedPeers.remove(claimedDeviceId);
    }
    public synchronized int activeCount() { return reservedPeers.size(); }
    public int pendingCount() {
        return NetworkConfig.MAX_PENDING_APPROVALS - pending.availablePermits();
    }
    @Override public synchronized void close() {
        closed = true;
        reservedPeers.clear();
    }
}
