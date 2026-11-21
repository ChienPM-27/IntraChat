package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupAuthorizationTest {
    private static GroupMember member(int id, GroupMember.Role role) {
        byte[] hash = new byte[32]; hash[0] = (byte) id;
        return new GroupMember(new UUID(0L, id), hash, role, "Peer " + id);
    }

    @Test void membersCanProposeInviteButOnlyOwnerCanApproveOrRemove() {
        GroupMember owner = member(1, GroupMember.Role.OWNER);
        GroupMember peer = member(2, GroupMember.Role.MEMBER);
        GroupRoster roster = GroupRoster.withOwner(owner).add(peer);
        GroupMetadata meta = new GroupMetadata(GroupIdentity.create("Nhóm"),
                owner.deviceId(), 3, 8);
        assertTrue(GroupAuthorization.canProposeInvite(roster, owner.deviceId()));
        assertTrue(GroupAuthorization.canProposeInvite(roster, peer.deviceId()));
        assertFalse(GroupAuthorization.canProposeInvite(roster, new UUID(0L, 9)));
        assertDoesNotThrow(() -> GroupAuthorization.requireOwner(meta, roster,
                owner.deviceId(), 3, 8));
        assertThrows(SecurityException.class, () -> GroupAuthorization.requireOwner(
                meta, roster, peer.deviceId(), 3, 8));
        assertThrows(SecurityException.class, () -> GroupAuthorization.requireOwner(
                meta, roster, owner.deviceId(), 2, 8));
        assertThrows(SecurityException.class, () -> GroupAuthorization.requireOwner(
                meta, roster, owner.deviceId(), 3, 7));
    }

    @Test void nameAndIpCannotAuthorizeOutsider() {
        GroupMember owner = member(1, GroupMember.Role.OWNER);
        GroupRoster roster = GroupRoster.withOwner(owner);
        GroupMetadata meta = new GroupMetadata(GroupIdentity.create("Team"),
                owner.deviceId(), 1, 1);
        assertThrows(SecurityException.class, () -> GroupAuthorization.requireMember(
                roster, new UUID(0L, 2)));
        assertThrows(SecurityException.class, () -> GroupAuthorization.requireOwner(
                meta, roster, new UUID(0L, 2), 1, 1));
    }
}
