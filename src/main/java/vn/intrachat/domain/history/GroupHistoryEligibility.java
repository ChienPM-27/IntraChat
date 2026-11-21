package vn.intrachat.domain.history;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure temporal membership policy. Does NOT authenticate owner or origin:
 * callers MUST first validate signed 004 owner epoch/roster/handoff and signer.
 */
public record GroupHistoryEligibility(UUID groupId, UUID memberId,
                                      GroupHistoryPosition join,
                                      GroupHistoryPosition removed) {
    private static final UUID ZERO = new UUID(0L,0L);

    public GroupHistoryEligibility {
        Objects.requireNonNull(groupId,"groupId");
        Objects.requireNonNull(memberId,"memberId");
        Objects.requireNonNull(join,"join");
        if (ZERO.equals(groupId) || ZERO.equals(memberId))
            throw new IllegalArgumentException("Reserved Group/member ID");
        if (removed!=null && removed.compareTo(join)<=0)
            throw new IllegalArgumentException("Removal must follow JOIN");
    }

    /**
     * The Group owner must recheck current member/owner verified state on
     * EVERY new frame. Once removed, NO new History is granted, even old events.
     */
    public boolean canIssue(GroupHistoryPosition event,
                            boolean currentOwnerVerified, boolean currentlyMember) {
        Objects.requireNonNull(event,"event");
        return currentOwnerVerified && currentlyMember && removed==null &&
            event.compareTo(join)>0;
    }
}
