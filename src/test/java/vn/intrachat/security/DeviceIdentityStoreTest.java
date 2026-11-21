package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeviceIdentityStoreTest {
    @TempDir Path directory;
    @Test void dpapiStoreSurvivesRestartAndContainsNoPlainPrivateKey() throws Exception {
        DeviceIdentityStore store=new DeviceIdentityStore(directory.resolve("identity.bin"));
        DeviceIdentity created=store.loadOrCreate();
        byte[] raw=Files.readAllBytes(store.path());
        assertTrue(raw.length>100);
        byte[] privateKey=created.keyPair().getPrivate().getEncoded();
        assertFalse(contains(raw,privateKey));
        DeviceIdentity again=new DeviceIdentityStore(store.path()).loadOrCreate();
        assertEquals(created.deviceId(),again.deviceId());
        assertArrayEquals(created.fingerprint(),again.fingerprint());
        assertArrayEquals(privateKey,again.keyPair().getPrivate().getEncoded());
    }
    @Test void corruptedProtectedIdentityFailsClosed() throws Exception {
        DeviceIdentityStore store=new DeviceIdentityStore(directory.resolve("damaged.bin"));
        DeviceIdentity original=store.loadOrCreate();
        Files.write(store.path(),new byte[]{1,2,3,4,5});
        assertThrows(Exception.class,store::loadOrCreate);
        assertTrue(Files.exists(store.path()));
    }
    private static boolean contains(byte[] a, byte[] b) {
        outer:for(int i=0;i<=a.length-b.length;i++){for(int j=0;j<b.length;j++)
            if(a[i+j]!=b[j])continue outer;return true;}return false;
    }
}
