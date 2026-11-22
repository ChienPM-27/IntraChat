package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.domain.MessageAttempt;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic monotonic clock: only status becomes UNCERTAIN; no network retry is scheduled. */
class AckDeadlineTest {
    @Test void exactlyThirtySecondsOfMissingAckMarksUncertainOnlyOnce(){
        AtomicLong clock=new AtomicLong(1000);
        var attempt=new MessageAttempt(UUID.randomUUID());
        attempt.startSending();attempt.localWriteCompleted();
        var deadline=new AckDeadline(clock::get,Duration.ofSeconds(30));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(29));
        assertFalse(deadline.markUncertainIfExpired(attempt));
        assertEquals(MessageAttempt.Status.LOCAL_SENT,attempt.status());
        clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
        assertTrue(deadline.markUncertainIfExpired(attempt));
        assertEquals(MessageAttempt.Status.UNCERTAIN,attempt.status());
        assertFalse(deadline.markUncertainIfExpired(attempt),
            "Do not generate a second timeout or any automatic send");
        attempt.onDeliveryAck();
        assertEquals(MessageAttempt.Status.APP_ACKNOWLEDGED,attempt.status());
        assertFalse(deadline.markUncertainIfExpired(attempt));
    }
    @Test void genuineDeliveryOrReadBeforeDeadlineNeverBecomesUncertain(){
        AtomicLong fake=new AtomicLong();
        for(boolean read:new boolean[]{false,true}){
            var attempt=new MessageAttempt(UUID.randomUUID());
            attempt.startSending();attempt.localWriteCompleted();
            var deadline=new AckDeadline(fake::get,Duration.ofSeconds(30));
            if(read)attempt.onViewReceipt();else attempt.onDeliveryAck();
            fake.addAndGet(TimeUnit.SECONDS.toNanos(31));
            assertFalse(deadline.markUncertainIfExpired(attempt));
            assertEquals(read?MessageAttempt.Status.READ:MessageAttempt.Status.APP_ACKNOWLEDGED,
                attempt.status());
        }
    }
}
