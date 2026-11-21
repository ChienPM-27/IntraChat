package vn.intrachat.domain.group;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupIdentityTest {
    @Test void createsUniqueGroupIdsEvenForSameDisplayName() {
        GroupIdentity first = GroupIdentity.create("Dự án 🚀");
        GroupIdentity second = GroupIdentity.create("Dự án 🚀");
        assertNotEquals(first.groupId(), second.groupId());
        assertEquals("Dự án 🚀", first.displayName());
        assertNotEquals(new UUID(0L, 0L), first.groupId());
        assertTrue(!first.createdAt().isAfter(Instant.now()));
    }

    @Test void validationRejectsEmptyOrUnboundedNamesAndIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> GroupIdentity.create("   "));
        assertThrows(IllegalArgumentException.class, () -> GroupIdentity.create("x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupIdentity(new UUID(0L, 0L), "valid", Instant.now()));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupIdentity(UUID.randomUUID(), "a\uD800b", Instant.now()));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupIdentity(UUID.randomUUID(), "valid", Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> GroupIdentity.create(null));
    }

    @Test void validatedMetadataHasIndependentPositiveEpochAndRevision() {
        GroupIdentity identity = GroupIdentity.create("Nhóm một");
        UUID owner = UUID.randomUUID();
        GroupMetadata meta = new GroupMetadata(identity, owner, 1, 1);
        assertEquals(1, meta.ownerEpoch());
        assertEquals(1, meta.membershipRevision());
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMetadata(identity, owner, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
            new GroupMetadata(identity, new UUID(0L, 0L), 1, 1));
    }
}
