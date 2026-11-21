package vn.intrachat.domain.group;

import java.util.Objects;

/**
 * Immutable in-memory authority state. Persistence, event signatures and transfer proofs
 * MUST be verified by application/security services before using this state on wire.
 */
public record GroupSnapshot(GroupMetadata metadata, GroupRoster roster,
                            MembershipEvent lastCommittedEvent) {
    public GroupSnapshot {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(roster, "roster");
        if (!metadata.ownerId().equals(roster.ownerId()))
            throw new IllegalArgumentException("Metadata owner and roster owner differ");
    }
}
