package ui;

import org.junit.jupiter.api.Test;
import ui.components.WindowsForegroundGuard;
import static org.junit.jupiter.api.Assertions.*;

class WindowsForegroundGuardTest {
    @Test void onlyExactNonzeroNativeWindowHandleCanReportForeground(){
        assertTrue(WindowsForegroundGuard.sameHandle(0x1111L,0x1111L));
        assertFalse(WindowsForegroundGuard.sameHandle(0x1111L,0x2222L));
        assertFalse(WindowsForegroundGuard.sameHandle(0,0));
        assertFalse(WindowsForegroundGuard.sameHandle(0x1111L,0));
    }
}
