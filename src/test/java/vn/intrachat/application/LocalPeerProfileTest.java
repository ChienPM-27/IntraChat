package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalPeerProfileTest {
    @TempDir Path temp;
    @Test void stableDeviceIdAcrossRestartsAndDifferentProfiles() throws Exception {
        Path a=temp.resolve("a/device.properties");
        var first=LocalPeerProfile.loadOrCreate(a);
        var restored=LocalPeerProfile.loadOrCreate(a);
        var other=LocalPeerProfile.loadOrCreate(temp.resolve("b/device.properties"));
        assertEquals(first.deviceId(),restored.deviceId());
        assertNotEquals(first.deviceId(),other.deviceId());
        assertEquals("Máy IntraChat",first.displayName());
    }
}
