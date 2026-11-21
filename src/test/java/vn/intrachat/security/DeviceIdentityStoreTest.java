package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DeviceIdentityStoreTest {
    @Test void protectedIdentityFileAclRestrictsExplicitAccessToCurrentOwner() throws Exception {
        DeviceIdentityStore store=new DeviceIdentityStore(directory.resolve("owner-only.bin"));
        store.loadOrCreate();
        var acl=Files.getFileAttributeView(store.path(),
                java.nio.file.attribute.AclFileAttributeView.class);
        assertNotNull(acl,"Windows ACL attribute view is required for DPAPI credential store");
        var owner=Files.getOwner(store.path());
        assertFalse(acl.getAcl().isEmpty());
        for(var entry:acl.getAcl()){
            if(entry.type()==java.nio.file.attribute.AclEntryType.ALLOW)
                assertEquals(owner,entry.principal(),
                    "No explicit allow ACE may grant another identity access");
        }
    }
    @Test void missingUnwritableSecureParentDoesNotProduceHalfWrittenCredential() throws Exception {
        Path occupied=directory.resolve("occupied");
        Files.writeString(occupied,"already a file");
        Path target=occupied.resolve("identity.bin");
        DeviceIdentityStore store=new DeviceIdentityStore(target);
        assertThrows(java.io.IOException.class,store::loadOrCreate);
        assertFalse(Files.exists(target));
        assertEquals("already a file",Files.readString(occupied));
    }
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
