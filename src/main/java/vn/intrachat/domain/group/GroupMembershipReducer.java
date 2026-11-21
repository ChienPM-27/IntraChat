package vn.intrachat.domain.group;

import java.util.Objects;

/** Deterministic replay-safe owner-revision reducer. No socket, trust or persistence side effect. */
public final class GroupMembershipReducer {
    private GroupMembershipReducer() { }

    public static GroupSnapshot apply(GroupSnapshot snapshot, MembershipEvent event) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(event, "event");
        if (!event.groupId().equals(snapshot.metadata().identity().groupId()) ||
            event.ownerEpoch() != snapshot.metadata().ownerEpoch())
            throw new SecurityException("Group or owner epoch mismatch");

        MembershipEvent last = snapshot.lastCommittedEvent();
        if (last != null && last.eventId().equals(event.eventId())) {
            if (last.equals(event)) return snapshot;
            throw new SecurityException("Conflicting membership event ID");
        }
        GroupAuthorization.requireOwner(snapshot.metadata(), snapshot.roster(),
                event.actorId(), event.ownerEpoch(), event.previousRevision());
        if (event.nextRevision() != snapshot.metadata().membershipRevision() + 1)
            throw new SecurityException("Invalid revision progression");

        GroupRoster next = switch (event.action()) {
            case JOIN -> snapshot.roster().add(event.joinedMember());
            case REMOVE -> snapshot.roster().remove(event.subjectId());
        };
        GroupMetadata nextMeta = new GroupMetadata(snapshot.metadata().identity(),
                snapshot.metadata().ownerId(), snapshot.metadata().ownerEpoch(),
                event.nextRevision());
        return new GroupSnapshot(nextMeta, next, event);
    }
}
