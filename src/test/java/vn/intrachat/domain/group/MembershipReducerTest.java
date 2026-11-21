package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MembershipReducerTest {
    private static GroupMember member(int id, GroupMember.Role role) {
        byte[] key = new byte[32]; key[0] = (byte) id;
        return new GroupMember(new UUID(0L, id), key, role, "Same alias");
    }

    private static GroupSnapshot initial(GroupMember owner) {
        return new GroupSnapshot(
                new GroupMetadata(GroupIdentity.create("Nhóm chat"), owner.deviceId(), 1, 1),
                GroupRoster.withOwner(owner), null);
    }

    @Test void joinAndRemoveCommitsHaveMonotonicRevisionAndAreIdempotent() {
        GroupMember owner = member(1, GroupMember.Role.OWNER);
        GroupMember b = member(2, GroupMember.Role.MEMBER);
        GroupSnapshot start = initial(owner);
        MembershipEvent joined = MembershipEvent.join(UUID.randomUUID(), start.metadata().identity().groupId(),
                1, 1, owner.deviceId(), b);
        GroupSnapshot afterJoin = GroupMembershipReducer.apply(start, joined);
        assertEquals(2, afterJoin.metadata().membershipRevision());
        assertTrue(afterJoin.roster().contains(b.deviceId()));
        assertSame(afterJoin, GroupMembershipReducer.apply(afterJoin, joined));
        assertThrows(SecurityException.class, () -> GroupMembershipReducer.apply(afterJoin,
                MembershipEvent.remove(UUID.randomUUID(), start.metadata().identity().groupId(),
                        1, 1, owner.deviceId(), b.deviceId())));
        MembershipEvent removed = MembershipEvent.remove(UUID.randomUUID(),
                start.metadata().identity().groupId(), 1, 2, owner.deviceId(), b.deviceId());
        GroupSnapshot afterRemove = GroupMembershipReducer.apply(afterJoin, removed);
        assertEquals(3, afterRemove.metadata().membershipRevision());
        assertFalse(afterRemove.roster().contains(b.deviceId()));
        assertSame(afterRemove, GroupMembershipReducer.apply(afterRemove, removed));
    }

    @Test void forgedActorOrEpochAndConflictingReplayNeverChangeRoster() {
        GroupMember owner = member(1, GroupMember.Role.OWNER);
        GroupMember b = member(2, GroupMember.Role.MEMBER);
        GroupSnapshot start = initial(owner);
        MembershipEvent valid = MembershipEvent.join(UUID.randomUUID(),
                start.metadata().identity().groupId(), 1, 1, owner.deviceId(), b);
        GroupSnapshot after = GroupMembershipReducer.apply(start, valid);
        MembershipEvent conflict = MembershipEvent.remove(valid.eventId(),
                start.metadata().identity().groupId(), 1, 1, owner.deviceId(), b.deviceId());
        assertThrows(SecurityException.class, () -> GroupMembershipReducer.apply(after, conflict));
        assertThrows(SecurityException.class, () -> GroupMembershipReducer.apply(start,
                MembershipEvent.join(UUID.randomUUID(), start.metadata().identity().groupId(),
                        1, 1, b.deviceId(), b)));
        assertThrows(SecurityException.class, () -> GroupMembershipReducer.apply(start,
                MembershipEvent.join(UUID.randomUUID(), start.metadata().identity().groupId(),
                        2, 1, owner.deviceId(), b)));
        assertThrows(IllegalStateException.class, () -> GroupMembershipReducer.apply(start,
                MembershipEvent.remove(UUID.randomUUID(), start.metadata().identity().groupId(),
                        1, 1, owner.deviceId(), owner.deviceId())));
    }
}
