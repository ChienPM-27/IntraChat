package vn.intrachat.transport;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

/** Synchronous socket connect primitive. Call only from an application worker, NEVER Swing EDT. */
public final class TcpConnector {
    private TcpConnector() {}
    public static Socket connect(NetworkConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Socket socket = new Socket();
        try {
            socket.connect(config.socketAddress(), NetworkConfig.CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            return socket;
        } catch (IOException | RuntimeException failure) {
            try { socket.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }
}
