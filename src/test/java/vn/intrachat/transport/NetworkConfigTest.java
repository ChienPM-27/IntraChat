package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NetworkConfigTest {
    @Test void validLiteralIpv4AndPort() {
        NetworkConfig config=new NetworkConfig("127.0.0.1",47321);
        assertEquals("127.0.0.1",config.ipv4());
        assertEquals(47321,config.port());
        assertNotNull(config.socketAddress());
    }
    @Test void malformedAddressesPortsRejected() {
        for(String s:new String[]{"localhost","example.com","256.1.1.2","192.168.1","0.0.0.0","255.255.255.255","224.0.0.1","fe80::1","","01.2.3.4"}) {
            assertThrows(IllegalArgumentException.class,()->new NetworkConfig(s,47321),s);
        }
        assertThrows(IllegalArgumentException.class,()->new NetworkConfig("127.0.0.1",0));
        assertThrows(IllegalArgumentException.class,()->new NetworkConfig("127.0.0.1",65536));
        assertThrows(IllegalArgumentException.class,()->new NetworkConfig("127.0.0.1",-1));
    }
}
