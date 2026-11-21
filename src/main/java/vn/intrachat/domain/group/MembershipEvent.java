package vn.intrachat.domain.group;

import java.util.Objects;
import java.util.UUID;

/** Immutable proposed owner commitment; signatures/durable commit are verified in higher layers. */
public record MembershipEvent(UUID eventId, UUID groupId, long ownerEpoch,
        long previousRevision, long nextRevision, UUID actorId, Action action,
        UUID subjectId, GroupMember joinedMember) {
    public enum Action { JOIN, REMOVE }

    public MembershipEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(subjectId, "subjectId");
        if (eventId.equals(new UUID(0,0)) || groupId.equals(new UUID(0,0)) ||
            actorId.equals(new UUID(0,0)) || subjectId.equals(new UUID(0,0)) ||
            ownerEpoch <= 0 || previousRevision <= 0 ||
            nextRevision != previousRevision + 1 || previousRevision == Long.MAX_VALUE)
            throw new IllegalArgumentException("Invalid membership event");
        if (action == Action.JOIN &&
            (joinedMember == null || !joinedMember.deviceId().equals(subjectId) ||
                joinedMember.role() != GroupMember.Role.MEMBER))
            throw new IllegalArgumentException("JOIN requires an identified member");
        if (action == Action.REMOVE && joinedMember != null)
            throw new IllegalArgumentException("REMOVE must not contain member grant");
    }

    public static MembershipEvent join(UUID eventId, UUID groupId, long epoch,
                                       long priorRevision, UUID owner, GroupMember added) {
        Objects.requireNonNull(added, "added");
        return new MembershipEvent(eventId, groupId, epoch, priorRevision, priorRevision+1,
                owner, Action.JOIN, added.deviceId(), added);
    }

    public static MembershipEvent remove(UUID eventId, UUID groupId, long epoch,
                                         long priorRevision, UUID owner, UUID removed) {
        return new MembershipEvent(eventId, groupId, epoch, priorRevision, priorRevision+1,
                owner, Action.REMOVE, removed, null);
    }
}
