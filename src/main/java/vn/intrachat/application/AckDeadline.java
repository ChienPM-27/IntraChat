package vn.intrachat.application;

import vn.intrachat.domain.MessageAttempt;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Pure monotonic clock ACK deadline; it never performs, queues or schedules any send. */
final class AckDeadline {
    private final LongSupplier clock;
    private final long startedNanos;
    private final long timeoutNanos;

    AckDeadline(LongSupplier clock,Duration timeout){
        this.clock=Objects.requireNonNull(clock);
        Objects.requireNonNull(timeout);
        if(timeout.isNegative()||timeout.isZero())
            throw new IllegalArgumentException("ACK timeout must be positive");
        timeoutNanos=timeout.toNanos();
        startedNanos=clock.getAsLong();
    }

    synchronized boolean markUncertainIfExpired(MessageAttempt attempt){
        Objects.requireNonNull(attempt);
        if(clock.getAsLong()-startedNanos<timeoutNanos)return false;
        MessageAttempt.Status status=attempt.status();
        if(status!=MessageAttempt.Status.LOCAL_SENT&&status!=MessageAttempt.Status.SENDING)
            return false;
        attempt.markUncertain();
        return true;
    }
}
