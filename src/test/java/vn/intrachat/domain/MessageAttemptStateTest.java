package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageAttemptStateTest {
    @Test void readIsReceiptDrivenAndMonotonic() {
        MessageAttempt state=new MessageAttempt(UUID.randomUUID());
        state.startSending();
        state.localWriteCompleted();
        assertEquals(MessageAttempt.Status.LOCAL_SENT,state.status());
        state.onViewReceipt();
        assertEquals(MessageAttempt.Status.READ,state.status());
        state.onDeliveryAck();
        assertEquals(MessageAttempt.Status.READ,state.status());
    }
    @Test void lostAckIsUncertainNotDefinitivelyFailed() {
        MessageAttempt state=new MessageAttempt(UUID.randomUUID());
        state.startSending();state.localWriteCompleted();state.markUncertain();
        assertEquals(MessageAttempt.Status.UNCERTAIN,state.status());
        state.onDeliveryAck();
        assertEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,state.status());
    }
    @Test void earlyApplicationAckCannotBeLostByLateLocalWriteCallback() {
        MessageAttempt state=new MessageAttempt(UUID.randomUUID());
        state.startSending();
        state.onDeliveryAck();
        state.localWriteCompleted();
        assertEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,state.status());
        state.onViewReceipt();
        state.localWriteCompleted();
        assertEquals(MessageAttempt.Status.READ,state.status());
    }
    @Test void readCannotOccurBeforeWrite() {
        MessageAttempt state=new MessageAttempt(UUID.randomUUID());
        assertThrows(IllegalStateException.class,state::onViewReceipt);
        assertThrows(IllegalStateException.class,state::onDeliveryAck);
    }
}
