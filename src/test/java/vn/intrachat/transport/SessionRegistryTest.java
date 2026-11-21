package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {
    @Test void pendingCapacityIsBoundedAndReleased() {
        SessionRegistry registry=new SessionRegistry();
        for(int i=0;i<4;i++)assertTrue(registry.tryReservePending());
        assertFalse(registry.tryReservePending());
        registry.releasePending();
        assertTrue(registry.tryReservePending());
        registry.close();
    }
    @Test void sameDeclaredDeviceCannotRegisterTwiceAndCloseClears() {
        SessionRegistry registry=new SessionRegistry();
        UUID remote=UUID.randomUUID();
        assertTrue(registry.reservePeer(remote));
        assertFalse(registry.reservePeer(remote));
        registry.releasePeer(remote);
        assertTrue(registry.reservePeer(remote));
        registry.close();
    }
}
