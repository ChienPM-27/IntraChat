package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppInfoTest {
    @Test void productAndStatusAreTruthful() {
        assertEquals("IntraChat", AppInfo.NAME);
        assertEquals("Chưa kết nối", AppInfo.INITIAL_CONNECTION_LABEL);
        assertFalse(AppInfo.CHAT_AVAILABLE);
    }
}
