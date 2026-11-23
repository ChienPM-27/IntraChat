package ui;
import org.junit.jupiter.api.Test;
import ui.components.WindowsForegroundGuard;
import static org.junit.jupiter.api.Assertions.*;
class WindowsForegroundGuardTest {
    @Test void nullCannotReportForeground(){
        assertFalse(WindowsForegroundGuard.isForeground(null));
    }
}
