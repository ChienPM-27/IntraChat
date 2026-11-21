package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionStateTest {
    @Test void initialStateIsDisconnected() {
        assertEquals(ConnectionState.DISCONNECTED, ConnectionState.initial());
    }
    @Test void presenceCannotImplyMessageRead() {
        assertNotEquals(MessageDeliveryState.READ.name(), ConnectionState.CONNECTED.name());
        assertNotEquals(MessageDeliveryState.DELIVERED.name(), ConnectionState.CONNECTED.name());
    }
}
