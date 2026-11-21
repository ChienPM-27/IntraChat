package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Inbound approval candidates are bounded before allocating each handler worker. */
class ResourceLimitsTest {
    @Test void fifthPendingCandidateIsRejectedWithoutCreatingAnotherWorker() throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            port = free.getLocalPort();
        }
        CountDownLatch allFourActive = new CountDownLatch(NetworkConfig.MAX_PENDING_APPROVALS);
        CountDownLatch release = new CountDownLatch(1);
        List<Socket> clients = new ArrayList<>();
        try (TcpListener listener = new TcpListener()) {
            listener.start(new NetworkConfig("127.0.0.1", port), candidate -> {
                try (candidate) {
                    allFourActive.countDown();
                    release.await(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                for (int i = 0; i < NetworkConfig.MAX_PENDING_APPROVALS; i++) {
                    clients.add(new Socket("127.0.0.1", port));
                }
                assertTrue(allFourActive.await(3, TimeUnit.SECONDS), "Four candidate workers should be active");
                try (Socket fifth = new Socket("127.0.0.1", port)) {
                    fifth.setSoTimeout(2000);
                    assertEquals(-1, fifth.getInputStream().read(),
                        "Fifth incoming TCP candidate must be dropped while approval slots are busy");
                }
            } finally {
                release.countDown();
                for (Socket client : clients) {
                    try { client.close(); } catch (Exception ignored) { }
                }
            }
        }
    }
}
