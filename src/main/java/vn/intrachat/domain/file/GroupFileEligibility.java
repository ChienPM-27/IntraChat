package vn.intrachat.domain.file;

/** Necessary pure epoch/roster fence. Caller MUST verify original author signatures,
 * current owner credentials and durable Group 004 owner-commit events upstream.
 * This method alone grants NO network permission.
 */
public final class GroupFileEligibility {
    private GroupFileEligibility() { }
    public static boolean canIssue(boolean currentOwnerVerified,boolean originalSignerVerified,
                                   boolean recipientCurrentMember,
                                   long currentOwnerEpoch,long claimedOwnerEpoch,
                                   long currentRosterRevision,long claimedRosterRevision,
                                   long offerCommittedAt,long recipientJoinedAt,
                                   Long recipientRemovedAt,long newChunkAt) {
        if (!currentOwnerVerified || !originalSignerVerified || !recipientCurrentMember)
            return false;
        if (currentOwnerEpoch<0 || claimedOwnerEpoch!=currentOwnerEpoch ||
            currentRosterRevision<0 || claimedRosterRevision!=currentRosterRevision)
            return false;
        if (offerCommittedAt<0 || recipientJoinedAt<0 || newChunkAt<offerCommittedAt)
            return false;
        if (recipientJoinedAt>offerCommittedAt) return false;
        return recipientRemovedAt==null ||
            (recipientRemovedAt>offerCommittedAt && newChunkAt<recipientRemovedAt);
    }
}
