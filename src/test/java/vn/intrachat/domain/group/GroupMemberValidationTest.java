package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupMemberValidationTest {
    @Test void invalidUnicodeOrControlCharacterCannotEnterVerifiedRosterMetadata() {
        byte[] pinned = new byte[32]; pinned[0] = 1;
        UUID peer = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMember(peer, pinned, GroupMember.Role.MEMBER, "Ali\uD800as"));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMember(peer, pinned, GroupMember.Role.MEMBER, "Ali\u0000as"));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMember(peer, pinned, GroupMember.Role.MEMBER, "\uDFFF"));
    }

    @Test void invalidIdentityCannotBecomeMember() {
        byte[] pin = new byte[32]; pin[0] = 2;
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMember(new UUID(0L, 0L), pin, GroupMember.Role.MEMBER, "A"));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMember(UUID.randomUUID(), new byte[4], GroupMember.Role.MEMBER, "A"));
    }
}
