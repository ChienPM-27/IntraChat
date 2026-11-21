package vn.intrachat.domain.history;

/**
 * Abstract owner-committed ordering key. Untrusted network-provided positions
 * MUST be verified against Group 004 signed owner and membership state first.
 */
public record GroupHistoryPosition(long ownerEpoch, long membershipRevision, long ownerSequence)
        implements Comparable<GroupHistoryPosition> {
    public GroupHistoryPosition {
        if (ownerEpoch < 1 || membershipRevision < 1 || ownerSequence < 1)
            throw new IllegalArgumentException("Unsigned/invalid Group commit position");
    }
    @Override public int compareTo(GroupHistoryPosition other) {
        int e=Long.compare(ownerEpoch,other.ownerEpoch);
        if (e!=0) return e;
        int s=Long.compare(ownerSequence,other.ownerSequence);
        return s!=0 ? s : Long.compare(membershipRevision,other.membershipRevision);
    }
}
