package vn.intrachat.domain.group;

import java.util.Objects;
import java.util.UUID;

/** Non-chat metadata; storage and authority proof are separate security responsibilities. */
public record GroupMetadata(GroupIdentity identity, UUID ownerId,
                            long ownerEpoch, long membershipRevision) {
    public GroupMetadata {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.equals(new UUID(0, 0)) ||
            ownerEpoch <= 0 || membershipRevision <= 0)
            throw new IllegalArgumentException("Invalid group owner or revision");
    }
}
