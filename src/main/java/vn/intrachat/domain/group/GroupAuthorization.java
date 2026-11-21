package vn.intrachat.domain.group;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure membership-role check. Caller MUST separately verify signed actor identity,
 * M2 TLS peer pin and durable committed membership before granting network authority.
 */
public final class GroupAuthorization {
    private GroupAuthorization() { }

    public static boolean canProposeInvite(GroupRoster roster, UUID actorId) {
        return roster != null && actorId != null && roster.contains(actorId);
    }

    public static void requireMember(GroupRoster roster, UUID actorId) {
        Objects.requireNonNull(roster, "roster");
        if (!canProposeInvite(roster, actorId))
            throw new SecurityException("Actor is not an active group member");
    }

    public static void requireOwner(GroupMetadata metadata, GroupRoster roster,
                                    UUID actorId, long claimedEpoch, long claimedRevision) {
        Objects.requireNonNull(metadata, "metadata");
        requireMember(roster, actorId);
        if (!metadata.ownerId().equals(roster.ownerId()) ||
            !metadata.ownerId().equals(actorId) ||
            metadata.ownerEpoch() != claimedEpoch ||
            metadata.membershipRevision() != claimedRevision)
            throw new SecurityException("Owner/epoch/membership authority mismatch");
    }
}
