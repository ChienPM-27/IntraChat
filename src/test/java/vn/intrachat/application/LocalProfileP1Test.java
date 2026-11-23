package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalProfileP1Test {
    @TempDir Path temp;
    @Test void persistsAndIsolates()throws Exception{
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("a"));
        LocalProfile again=LocalProfile.loadOrCreate(temp.resolve("a"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("b"));
        assertEquals(a.deviceId(),again.deviceId());
        assertEquals(a.displayName(),again.displayName());
        assertNotEquals(a.deviceId(),b.deviceId());
        assertTrue(Files.isRegularFile(temp.resolve("a/profile.json")));
    }
    @Test void corruptedProfileDoesNotSilentlyRotateIdentity()throws Exception{
        Path dir=temp.resolve("broken");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("profile.json"),"{broken");
        assertThrows(java.io.IOException.class,()->LocalProfile.loadOrCreate(dir));
        assertEquals("{broken",Files.readString(dir.resolve("profile.json")));
    }
}
