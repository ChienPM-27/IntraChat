package vn.intrachat.transport;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** Validates literal IPv4 addresses: no DNS lookup or discovery. */
public record NetworkConfig(String ipv4, int port) {
    public static final int DEFAULT_PORT = 47321;
    public static final int MAX_ACTIVE_SESSIONS = 4;
    public static final int MAX_PENDING_APPROVALS = 4;
    public static final int MAX_OUTBOUND_FRAMES = 16;
    public static final int MAX_QUEUED_BYTES = 2 * 1024 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int HELLO_TIMEOUT_MS = 5000;
    public static final int APPROVAL_TIMEOUT_MS = 30000;
    public static final int ACTIVE_READ_TIMEOUT_MS = 15000;

    public NetworkConfig {
        Objects.requireNonNull(ipv4, "ipv4");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1..65535");
        parseIpv4(ipv4);
    }

    public InetSocketAddress socketAddress() {
        return new InetSocketAddress(parseIpv4(ipv4), port);
    }

    public static Inet4Address parseIpv4(String address) {
        Objects.requireNonNull(address, "address");
        String[] fragments = address.split("\\.", -1);
        if (fragments.length != 4) throw new IllegalArgumentException("IPv4 literal required");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = fragments[i];
            if (!part.matches("(0|[1-9][0-9]{0,2})")) {
                throw new IllegalArgumentException("Invalid IPv4 segment");
            }
            int value = Integer.parseInt(part);
            if (value > 255) throw new IllegalArgumentException("Invalid IPv4 value");
            bytes[i] = (byte) value;
        }
        int first = bytes[0] & 0xff;
        if (first == 0 || first >= 224 || (bytes[0] == (byte) 255)) {
            throw new IllegalArgumentException("Multicast/unspecified/broadcast IP not supported");
        }
        try {
            return (Inet4Address) InetAddress.getByAddress(bytes);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("Invalid IPv4 bytes", impossible);
        }
    }
}
