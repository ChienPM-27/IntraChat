package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupRosterTest {
    private static GroupMember member(int id, GroupMember.Role role, String name) {
        byte[] key = new byte[32];
        key[0] = (byte) id;
        return new GroupMember(new UUID(0, id), key, role, name);
    }

    @Test void ownerPlusFourMembersIsAllowedButSixthRejected() {
        GroupRoster roster = GroupRoster.withOwner(member(1, GroupMember.Role.OWNER, "Same"));
        for (int i=2; i<=5; i++)
            roster = roster.add(member(i, GroupMember.Role.MEMBER, "Same"));
        assertEquals(5, roster.size());
        assertThrows(IllegalStateException.class, () ->
            GroupRoster.withOwner(member(1, GroupMember.Role.OWNER, "Same"))
                .add(member(2, GroupMember.Role.MEMBER, "B"))
                .add(member(3, GroupMember.Role.MEMBER, "C"))
                .add(member(4, GroupMember.Role.MEMBER, "D"))
                .add(member(5, GroupMember.Role.MEMBER, "E"))
                .add(member(6, GroupMember.Role.MEMBER, "F")));
        assertEquals(5, roster.members().size());
        GroupRoster fullRoster = roster;
        assertThrows(UnsupportedOperationException.class, () ->
            fullRoster.members().add(member(6, GroupMember.Role.MEMBER, "F")));
    }

    @Test void rejectsDuplicateDeviceOrFingerprintAndSecondOwner() {
        GroupRoster roster = GroupRoster.withOwner(member(1, GroupMember.Role.OWNER, "Same"));
        GroupRoster finalRoster = roster.add(member(2, GroupMember.Role.MEMBER, "Same"));
        assertThrows(IllegalArgumentException.class, () ->
            finalRoster.add(member(2, GroupMember.Role.MEMBER, "Other")));
        byte[] key2 = new byte[32];key2[0] = 2;
        assertThrows(IllegalArgumentException.class, () ->
            finalRoster.add(new GroupMember(new UUID(0, 3), key2, GroupMember.Role.MEMBER, "New")));
        assertThrows(IllegalArgumentException.class, () ->
            finalRoster.add(member(3, GroupMember.Role.OWNER, "Other")));
        assertEquals(new UUID(0, 1), finalRoster.ownerId());
    }

    @Test void pinIsDefensivelyCopiedAndOwnerCannotBeRemoved() {
        byte[] hash = new byte[32]; hash[0] = 9;
        GroupMember owner = new GroupMember(new UUID(0, 9), hash, GroupMember.Role.OWNER, "O");
        hash[0] = 0;
        assertEquals(9, owner.pinnedSpkiSha256()[0]);
        byte[] retrieved = owner.pinnedSpkiSha256();
        retrieved[0] = 0;
        assertEquals(9, owner.pinnedSpkiSha256()[0]);
        GroupRoster roster = GroupRoster.withOwner(owner);
        assertThrows(IllegalStateException.class, () -> roster.remove(owner.deviceId()));
        assertFalse(roster.contains(new UUID(0, 10)));
    }
}
