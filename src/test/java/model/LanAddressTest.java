package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LanAddressTest {
    @Test
    void normalizesNumericAddresses() {
        assertEquals("192.168.1.23", LanAddress.normalize(" 192.168.1.23 "));
        assertEquals(LanAddress.normalize("fd00::23"), LanAddress.normalize("FD00:0000:0:0:0:0:0:0023"));
        assertEquals("192.168.1.23", LanAddress.normalize("::ffff:192.168.1.23"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0xC0A80117", "192.168.1", "192.168.1.256", "192.168.01.23",
            "192.168.1.23:80", "192.168.1.23/24", "intrachat.local", "[fd00::23]:80", "fd00:::23", "fd00::x"})
    void rejectsNonLiteralOrMalformedAddresses(String input) {
        assertThrows(IllegalArgumentException.class, () -> LanAddress.normalize(input));
    }
}
