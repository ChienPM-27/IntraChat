package model.mock;

import model.CallSession;
import model.ChatUser;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static model.CallSession.Direction.INCOMING;
import static model.CallSession.Status.*;

final class MockCalls {
    private static final Duration CONNECT_DELAY = Duration.ofSeconds(2);
    private final ScheduledExecutorService worker;
    private final Consumer<CallSession> updates;
    private final Duration incomingTimeout;
    private CallSession current;
    private ScheduledFuture<?> ringing;

    MockCalls(ScheduledExecutorService worker, Consumer<CallSession> updates, Duration incomingTimeout) {
        this.worker = worker;
        this.updates = updates;
        this.incomingTimeout = incomingTimeout;
    }

    void ring(String conversationId, ChatUser peer, CallSession.Mode mode, CallSession.Direction direction) {
        if (current != null && current.status().ongoing()) {
            throw new IllegalArgumentException("Hãy kết thúc cuộc gọi hiện tại trước.");
        }
        String callId = UUID.randomUUID().toString();
        current = new CallSession(callId, conversationId, peer, mode, direction, RINGING, null, null);
        boolean incoming = direction == INCOMING;
        Duration delay = incoming ? incomingTimeout : CONNECT_DELAY;
        ringing = worker.schedule(() -> {
            if (current.id().equals(callId) && current.status() == RINGING) {
                ringing = null;
                transition(incoming ? MISSED : CONNECTED);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        updates.accept(current);
    }

    void respond(String callId, boolean accept) {
        requireOngoing(callId);
        if (current.status() != RINGING || current.direction() != INCOMING) {
            throw new IllegalArgumentException("Lời gọi này không chờ bạn trả lời.");
        }
        transition(accept ? CONNECTED : DECLINED);
    }

    void end(String callId) {
        requireOngoing(callId);
        CallSession.Status next = current.status() == CONNECTED ? ENDED
                : current.direction() == INCOMING ? DECLINED : CANCELLED;
        transition(next);
    }

    private void requireOngoing(String callId) {
        if (current == null || !current.id().equals(callId) || !current.status().ongoing()) {
            throw new IllegalArgumentException("Cuộc gọi đã đóng.");
        }
    }

    private void transition(CallSession.Status next) {
        if (ringing != null) {
            ringing.cancel(false);
            ringing = null;
        }
        Instant now = Instant.now();
        current = new CallSession(current.id(), current.conversationId(), current.peer(), current.mode(),
                current.direction(), next, next == CONNECTED ? now : current.connectedAt(), next.ongoing() ? null : now);
        updates.accept(current);
    }
}
