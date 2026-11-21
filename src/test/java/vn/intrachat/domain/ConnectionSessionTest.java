package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionSessionTest {
    @Test void approvedAndHandshakeReadyBeforeConnected() {
        ConnectionSession session=new ConnectionSession(UUID.randomUUID());
        assertEquals(ConnectionState.DISCONNECTED,session.state());
        session.beginConnecting();
        assertEquals(ConnectionState.CONNECTING,session.state());
        session.waitingApproval();
        assertNotEquals(ConnectionState.CONNECTED,session.state());
        assertThrows(IllegalStateException.class,session::markReady);
        session.approve();
        assertNotEquals(ConnectionState.CONNECTED,session.state());
        session.markReady();
        assertEquals(ConnectionState.CONNECTED,session.state());
        session.close();
        assertEquals(ConnectionState.DISCONNECTED,session.state());
        assertThrows(IllegalStateException.class,session::markReady);
    }
    @Test void rejectedCannotBecomeConnected() {
        ConnectionSession s=new ConnectionSession(UUID.randomUUID());
        s.beginConnecting(); s.waitingApproval(); s.reject();
        assertEquals(ConnectionState.FAILED,s.state());
        assertThrows(IllegalStateException.class,s::approve);
        assertThrows(IllegalStateException.class,s::markReady);
    }
}
