package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PeerIdentityTest {
    @Test void idIsNotDisplayName() {
        UUID id = UUID.randomUUID();
        PeerIdentity a = new PeerIdentity(id, "Lan");
        PeerIdentity renamed = a.withDisplayName("New name");
        assertEquals(id, renamed.deviceId());
        assertNotEquals(a.displayName(), renamed.displayName());
        PeerIdentity duplicateName = new PeerIdentity(UUID.randomUUID(), "Lan");
        assertNotEquals(a.deviceId(), duplicateName.deviceId());
    }
    @Test void invalidIdentityIsRejected() {
        assertThrows(NullPointerException.class, () -> new PeerIdentity(null, "Name"));
        assertThrows(IllegalArgumentException.class, () -> new PeerIdentity(UUID.randomUUID(), "  "));
        assertThrows(IllegalArgumentException.class, () -> new PeerIdentity(UUID.randomUUID(), "x".repeat(129)));
    }
    @Test void unicodeNamesSurvive() {
        assertEquals("Hoài 😊", new PeerIdentity(UUID.randomUUID(), "Hoài 😊").displayName());
    }
}
