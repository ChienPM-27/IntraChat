package vn.intrachat.transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Starts a TCP listener only after explicit user action. Never auto discovers peers. */
public final class TcpListener implements AutoCloseable {
    private final ExecutorService acceptWorkers = Executors.newVirtualThreadPerTaskExecutor();
    // Limit candidates before allocating a virtual thread for user approval/HELLO handling.
    private final java.util.concurrent.Semaphore candidateSlots =
        new java.util.concurrent.Semaphore(NetworkConfig.MAX_PENDING_APPROVALS);
    private ServerSocket server;
    private volatile boolean listening;

    public synchronized void start(NetworkConfig config, Consumer<Socket> handler) throws IOException {
        Objects.requireNonNull(config);
        Objects.requireNonNull(handler);
        if (listening) throw new IllegalStateException("Listener already started");
        ServerSocket target = new ServerSocket();
        try {
            target.setReuseAddress(true);
            target.bind(config.socketAddress(), 8);
        } catch (IOException failure) {
            try { target.close(); } catch (IOException ignored) { }
            throw failure;
        }
        server = target;
        listening = true;
        acceptWorkers.submit(() -> {
            while (listening) {
                try {
                    Socket candidate = target.accept();
                    candidate.setTcpNoDelay(true);
                    if (!candidateSlots.tryAcquire()) {
                        candidate.close(); // bounded rejection before spawning a worker
                        continue;
                    }
                    try {
                        acceptWorkers.submit(() -> {
                            try { handler.accept(candidate); }
                            catch (RuntimeException failure) {
                                try { candidate.close(); } catch (IOException ignored) { }
                            } finally {
                                candidateSlots.release();
                            }
                        });
                    } catch (java.util.concurrent.RejectedExecutionException closing) {
                        candidateSlots.release();
                        candidate.close();
                        if (!listening) return;
                    }
                } catch (IOException failure) {
                    if (!listening) return;
                    // Fail closed on listener errors; restart only by user.
                    listening = false;
                    try { target.close(); } catch (IOException ignored) { }
                    return;
                }
            }
        });
    }

    public boolean isListening() { return listening && server != null && !server.isClosed(); }

    @Override public synchronized void close() {
        listening = false;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
            server = null;
        }
        acceptWorkers.shutdownNow();
    }
}
