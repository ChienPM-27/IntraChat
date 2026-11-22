package vn.intrachat.transport;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-TCP diagnostic queue admission with independent frame and byte caps.
 * The scoped reservation is idempotent; failed writes and cancellations must
 * always release exactly the permits reserved, never duplicate them.
 */
final class OutboundBudget {
    private final int frameLimit;
    private final int byteLimit;
    private int frames;
    private int bytes;

    OutboundBudget(int frameLimit, int byteLimit) {
        if (frameLimit < 1 || byteLimit < 1)
            throw new IllegalArgumentException("Queue bounds must be positive");
        this.frameLimit = frameLimit;
        this.byteLimit = byteLimit;
    }

    synchronized Reservation tryReserve(int frameBytes) {
        if (frameBytes < 1 || frameBytes > byteLimit
                || frames >= frameLimit || frameBytes > byteLimit - bytes)
            return null;
        frames++;
        bytes += frameBytes;
        return new Reservation(frameBytes);
    }

    synchronized int queuedFrames() { return frames; }
    synchronized int queuedBytes() { return bytes; }

    final class Reservation implements AutoCloseable {
        private final int size;
        private final AtomicBoolean released = new AtomicBoolean();
        private Reservation(int size) { this.size = size; }
        @Override public void close() {
            if (!released.compareAndSet(false, true)) return;
            synchronized (OutboundBudget.this) {
                frames--;
                bytes -= size;
                if (frames < 0 || bytes < 0)
                    throw new IllegalStateException("Outbound budget underflow");
            }
        }
    }
}
