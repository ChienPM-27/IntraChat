package vn.intrachat.transport;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Local interface inspection only; no network probes or peer discovery. */
public final class LocalIpv4 {
    private LocalIpv4() {}

    public static List<String> detect() {
        List<Inet4Address> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : NetworkInterface.networkInterfaces().toList()) {
                try {
                    if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()) continue;
                    nic.inetAddresses().filter(Inet4Address.class::isInstance)
                        .map(Inet4Address.class::cast).forEach(addresses::add);
                } catch (SocketException ignored) {
                    // A disconnected or inaccessible interface must not block startup.
                }
            }
        } catch (SocketException ignored) {
            // The manual address field remains available even without interface enumeration.
        }
        return candidates(addresses);
    }

    static List<String> candidates(List<Inet4Address> addresses) {
        List<String> result = new ArrayList<>(addresses.stream()
            .filter(Inet4Address::isSiteLocalAddress)
            .map(Inet4Address::getHostAddress)
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList());
        // Never automatically bind public, CGNAT or link-local addresses.
        // Explicit manual IPv4 configuration is still supported.
        result.add("127.0.0.1");
        return List.copyOf(result);
    }
}
