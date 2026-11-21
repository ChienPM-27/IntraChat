package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PairingStateMachineTest {
    @Test void bothHumansAndBothReadyAreRequired() {
        PairingStateMachine state=new PairingStateMachine(UUID.randomUUID());
        assertFalse(state.maySendChat());
        state.acceptLocalFingerprint();
        assertFalse(state.maySendChat());
        state.acceptRemoteConfirmation();
        assertFalse(state.maySendChat());
        state.localReady();
        assertFalse(state.maySendChat());
        state.remoteReady();
        assertTrue(state.maySendChat());
        state.reject();
        assertFalse(state.maySendChat());
    }
    @Test void rejectOrTimeoutNeverCreatesTrust() {
        PairingStateMachine state=new PairingStateMachine(UUID.randomUUID());
        state.acceptRemoteConfirmation();
        state.reject();
        assertThrows(IllegalStateException.class,state::acceptLocalFingerprint);
        assertFalse(state.maySendChat());
    }
}
