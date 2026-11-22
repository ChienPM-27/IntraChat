package vn.intrachat.transport;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Bounded M1 diagnostic session registry. Remote UUIDs are self-declared labels,
 * NOT authenticated identity. Directions and immutable session tokens prevent
 * concurrent cross-connect and stale close callbacks from evicting a newer socket.
 */
public final class SessionRegistry implements AutoCloseable {
    public enum Direction { INBOUND, OUTBOUND }
    public enum Reservation { ACCEPTED, REPLACED, REJECTED }
    private record Lease(UUID token, Direction direction) {}
    private final Semaphore pending = new Semaphore(NetworkConfig.MAX_PENDING_APPROVALS);
    private final Map<UUID, Lease> reservedPeers = new HashMap<>();
    private boolean closed;

    public synchronized boolean tryReservePending() {
        return !closed && pending.tryAcquire();
    }

    /** Balanced release; extra releases must never increase admission capacity. */
    public synchronized void releasePending() {
        if (pending.availablePermits() >= NetworkConfig.MAX_PENDING_APPROVALS)
            throw new IllegalStateException("No pending session slot to release");
        pending.release();
    }

    /**
     * Lower UUID initiates when simultaneous cross-connects compete. For a
     * single connection the first candidate is accepted irrespective of direction.
     * If both arrive, a preferred-direction candidate can replace the old lease;
     * the caller then closes the losing socket. Equal-direction races choose the
     * unsigned-lowest session UUID so the selection is deterministic.
     */
    public synchronized Reservation reservePeer(UUID claimedDeviceId, UUID sessionToken,
                                                 Direction direction, UUID localDeviceId) {
        if (closed || claimedDeviceId == null || sessionToken == null
                || localDeviceId == null || direction == null
                || isZero(claimedDeviceId) || isZero(sessionToken) || isZero(localDeviceId)
                || claimedDeviceId.equals(localDeviceId)) {
            return Reservation.REJECTED;
        }
        Lease old = reservedPeers.get(claimedDeviceId);
        if (old == null) {
            if (reservedPeers.size() >= NetworkConfig.MAX_ACTIVE_SESSIONS)
                return Reservation.REJECTED;
            reservedPeers.put(claimedDeviceId, new Lease(sessionToken, direction));
            return Reservation.ACCEPTED;
        }
        if (old.token().equals(sessionToken)) return Reservation.REJECTED;
        Direction preferred = compareUuid(localDeviceId, claimedDeviceId) < 0
                ? Direction.OUTBOUND : Direction.INBOUND;
        if (old.direction() != direction) {
            if (direction != preferred) return Reservation.REJECTED;
        } else if (compareUuid(sessionToken, old.token()) >= 0) {
            return Reservation.REJECTED;
        }
        reservedPeers.put(claimedDeviceId, new Lease(sessionToken, direction));
        return Reservation.REPLACED;
    }

    /** Legacy reservation for existing M1 callers/tests; no trusted identity implied. */
    public synchronized boolean reservePeer(UUID claimedDeviceId) {
        if (closed || claimedDeviceId == null || isZero(claimedDeviceId)
                || reservedPeers.containsKey(claimedDeviceId)
                || reservedPeers.size() >= NetworkConfig.MAX_ACTIVE_SESSIONS) return false;
        reservedPeers.put(claimedDeviceId, new Lease(UUID.randomUUID(), null));
        return true;
    }

    /** Release only the exact socket that owns the lease, not a replacement. */
    public synchronized boolean releasePeer(UUID claimedDeviceId, UUID sessionToken) {
        Lease lease = reservedPeers.get(claimedDeviceId);
        if (lease == null || !lease.token().equals(sessionToken)) return false;
        reservedPeers.remove(claimedDeviceId);
        return true;
    }

    /** Compatibility method for legacy callers; prefer token-bound release. */
    public synchronized void releasePeer(UUID claimedDeviceId) {
        reservedPeers.remove(claimedDeviceId);
    }

    public synchronized int activeCount() { return reservedPeers.size(); }
    public synchronized int pendingCount() {
        return NetworkConfig.MAX_PENDING_APPROVALS - pending.availablePermits();
    }

    @Override public synchronized void close() {
        closed = true;
        reservedPeers.clear();
    }

    private static boolean isZero(UUID id) { return id.equals(new UUID(0, 0)); }
    private static int compareUuid(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high == 0 ? Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits()) : high;
    }
}
