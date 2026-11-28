package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalIpv4Test {
    @Test void prefersPrivateAddressesAndExcludesUnsuitableAddresses() {
        var addresses = List.of("192.168.1.8", "10.1.2.3", "192.168.1.8",
            "172.16.3.4", "127.0.0.1", "169.254.1.1", "100.85.1.1", "8.8.8.8")
            .stream().map(NetworkConfig::parseIpv4).toList();
        assertEquals(List.of("10.1.2.3", "172.16.3.4", "192.168.1.8", "127.0.0.1"),
            LocalIpv4.candidates(addresses));
    }

    @Test void fallsBackToLoopbackWithoutLanAddress() {
        assertEquals(List.of("127.0.0.1"), LocalIpv4.candidates(List.of()));
    }

    @Test void detectedAddressesAreValidAndIncludeFallback() {
        var addresses = LocalIpv4.detect();
        assertFalse(addresses.isEmpty());
        assertEquals("127.0.0.1", addresses.getLast());
        for (String address : addresses) {
            assertDoesNotThrow(() -> new NetworkConfig(address, NetworkConfig.DEFAULT_PORT));
        }
    }
}
