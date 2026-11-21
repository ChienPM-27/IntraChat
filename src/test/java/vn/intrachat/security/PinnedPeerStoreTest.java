package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PinnedPeerStoreTest {
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
