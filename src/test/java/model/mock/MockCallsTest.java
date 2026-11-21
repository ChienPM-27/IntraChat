package model.mock;

import model.CallSession;
import model.ChatUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static model.CallSession.Direction.*;
import static model.CallSession.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class MockCallsTest {
    private final ChatUser peer = new ChatUser("peer", "Mai Anh", true);
    private final ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
    private final BlockingQueue<CallSession> updates = new LinkedBlockingQueue<>();

    @AfterEach
    void stopWorker() {
        worker.shutdownNow();
    }

    @Test
    void cancelsOutgoingCallAndRejectsStaleOperations() throws Exception {
        worker.setRemoveOnCancelPolicy(true);
        var calls = new MockCalls(worker, updates::add, Duration.ofSeconds(20));
        run(() -> {
            calls.ring("direct", peer, CallSession.Mode.VOICE, OUTGOING);
            String id = updates.element().id();
            assertThrows(IllegalArgumentException.class,
                    () -> calls.ring("other", peer, CallSession.Mode.VIDEO, INCOMING));
            assertThrows(IllegalArgumentException.class, () -> calls.respond(id, true));
            calls.end(id);
            assertTrue(worker.getQueue().isEmpty());
        });
        CallSession ringing = next();
        CallSession cancelled = next();
        assertEquals(RINGING, ringing.status());
        assertEquals(CANCELLED, cancelled.status());
        assertEquals(ringing.id(), cancelled.id());
        var failure = assertThrows(ExecutionException.class, () -> run(() -> calls.end(ringing.id())));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void acceptsIncomingCallAndFreezesDurationOnHangup() throws Exception {
        var calls = new MockCalls(worker, updates::add, Duration.ofSeconds(20));
        run(() -> calls.ring("direct", peer, CallSession.Mode.VIDEO, INCOMING));
        CallSession ringing = next();
        run(() -> calls.respond(ringing.id(), true));
        CallSession connected = next();
        assertEquals(CONNECTED, connected.status());
        assertEquals(ringing.id(), connected.id());
        assertEquals(1_500, connected.durationMillis(connected.connectedAt().plusMillis(1_500)));
        run(() -> calls.end(ringing.id()));
        CallSession ended = next();
        assertEquals(ENDED, ended.status());
        assertEquals(connected.connectedAt(), ended.connectedAt());
        assertEquals(ended.durationMillis(ended.endedAt()), ended.durationMillis(Instant.MAX));
    }

    @Test
    void expiresUnansweredCall() throws Exception {
        var calls = new MockCalls(worker, updates::add, Duration.ofMillis(20));
        run(() -> calls.ring("direct", peer, CallSession.Mode.VIDEO, INCOMING));
        assertEquals(RINGING, next().status());
        CallSession missed = next();
        assertEquals(MISSED, missed.status());
        assertEquals(0, missed.durationMillis(Instant.now()));
    }

    private void run(Runnable action) throws Exception {
        worker.submit(action).get(5, TimeUnit.SECONDS);
    }

    private CallSession next() throws InterruptedException {
        CallSession session = updates.poll(5, TimeUnit.SECONDS);
        assertNotNull(session);
        return session;
    }
}
