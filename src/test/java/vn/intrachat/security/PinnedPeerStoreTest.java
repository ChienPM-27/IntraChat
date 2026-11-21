package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PinnedPeerStoreTest {
    @Test void samePinnedPublicKeyCannotBeSilentlyMigratedToDifferentDeviceId() throws Exception {
        PinnedPeerStore trust=new PinnedPeerStore(dir.resolve("pin-collision.bin"));
        UUID first=UUID.randomUUID(),other=UUID.randomUUID();
        byte[] fingerprint=new byte[32];java.util.Arrays.fill(fingerprint,(byte)0x52);
        trust.pinNew(first,fingerprint);
        assertThrows(IllegalStateException.class,()->trust.pinNew(other,fingerprint),
            "A key pinned to one device ID must not become a second trusted identity");
        assertFalse(trust.known(other));
        assertTrue(trust.matches(first,fingerprint));
    }
    @Test void protectedTrustStoreCorruptionNeverBecomesEmptyTrustedDatabase() throws Exception {
        Path file=dir.resolve("pins-corrupt.bin");
        PinnedPeerStore trust=new PinnedPeerStore(file);
        UUID first=UUID.randomUUID();byte[] pin=new byte[32];pin[0]=13;
        trust.pinNew(first,pin);
        byte[] original=Files.readAllBytes(file);
        Files.write(file,new byte[]{1,2,3,4});
        assertThrows(java.io.IOException.class,()->trust.known(first));
        assertThrows(java.io.IOException.class,()->trust.pinNew(UUID.randomUUID(),pin));
        assertArrayEquals(new byte[]{1,2,3,4},Files.readAllBytes(file),
            "Corrupt store may not be silently rewritten");
        assertTrue(original.length>4);
    }
    @TempDir Path dir;
    @Test void pinIsRememberedButChangedKeyNeverSilentlyReplaces() throws Exception {
        PinnedPeerStore store=new PinnedPeerStore(dir.resolve("trust.bin"));
        UUID peer=UUID.randomUUID();byte[] key=new byte[32];java.util.Arrays.fill(key,(byte)7);
        store.pinNew(peer,key);
        PinnedPeerStore restarted=new PinnedPeerStore(dir.resolve("trust.bin"));
        assertTrue(restarted.matches(peer,key));
        byte[] changed=key.clone();changed[0]=8;
        assertFalse(restarted.matches(peer,changed));
        assertThrows(IllegalStateException.class,()->restarted.pinNew(peer,changed));
        assertFalse(new String(Files.readAllBytes(store.path()),java.nio.charset.StandardCharsets.ISO_8859_1).contains(peer.toString()));
        restarted.revoke(peer);
        assertFalse(new PinnedPeerStore(store.path()).matches(peer,key));
    }
}
