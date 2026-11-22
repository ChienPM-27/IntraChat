package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Fake clock: never sleep 30 seconds or make a real network connection. */
class HeartbeatTest {
    @Test void sendsProbeEveryScheduledTenSecondsAndClosesAfterThirtySecondsSilence(){
        AtomicLong clock=new AtomicLong();
        DiagnosticHeartbeat state=new DiagnosticHeartbeat(clock::get);
        assertEquals(DiagnosticHeartbeat.Decision.PING, state.tick());
        clock.set(TimeUnit.SECONDS.toNanos(10));
        assertEquals(DiagnosticHeartbeat.Decision.PING, state.tick());
        clock.set(TimeUnit.SECONDS.toNanos(29));
        assertEquals(DiagnosticHeartbeat.Decision.PING, state.tick());
        clock.set(TimeUnit.SECONDS.toNanos(30));
        assertEquals(DiagnosticHeartbeat.Decision.CLOSE, state.tick());
        assertEquals(DiagnosticHeartbeat.Decision.NONE, state.tick());
    }
    @Test void onlyValidInboundTrafficExtendsLivenessWithoutAutoReconnect(){
        AtomicLong clock=new AtomicLong();
        DiagnosticHeartbeat state=new DiagnosticHeartbeat(clock::get);
        clock.set(TimeUnit.SECONDS.toNanos(25));
        state.recordInbound();
        clock.set(TimeUnit.SECONDS.toNanos(44));
        assertEquals(DiagnosticHeartbeat.Decision.PING,state.tick());
        clock.set(TimeUnit.SECONDS.toNanos(55));
        assertEquals(DiagnosticHeartbeat.Decision.CLOSE,state.tick());
        state.recordInbound(); // stale traffic cannot resurrect a closed socket
        assertEquals(DiagnosticHeartbeat.Decision.NONE,state.tick());
    }
    @Test void stopCancelsAllFuturePingsAndCannotResurrect(){
        AtomicLong clock=new AtomicLong();
        DiagnosticHeartbeat state=new DiagnosticHeartbeat(clock::get);
        state.close();
        for(int i=0;i<20;i++){
            clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
            assertEquals(DiagnosticHeartbeat.Decision.NONE,state.tick());
        }
        assertTrue(state.isClosed());
    }
}
