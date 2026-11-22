package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stored ciphertext can be rolled back by the filesystem: only live owner
 * validation over an authenticated GROUP-capable TLS session can unlock it.
 */
class GroupRestoreStaleTest {
    @TempDir Path dir;
    private final UUID owner=UUID.randomUUID(),member=UUID.randomUUID(),group=UUID.randomUUID();
    private static KeyPair keys()throws Exception{
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }
    private GroupMetadata signed(KeyPair key,long rev,byte[] prev)throws Exception{
        byte[] pin=FingerprintVerifier.spkiFingerprint(key.getPublic());
        byte[] other=new byte[32];Arrays.fill(other,(byte)22);
        GroupRoster roster=new GroupRoster(
            new GroupIdentity(group,"Nhóm bảo vệ",owner,pin),
            rev,prev,List.of(new GroupMember(owner,pin,GroupMember.Role.OWNER),
                new GroupMember(member,other,GroupMember.Role.MEMBER)),
            UUID.randomUUID(),Instant.parse("2026-11-22T09:00:00Z").plusSeconds(rev));
        return new GroupMetadata(roster,GroupOwnerSignature.signRoster(roster,
            key.getPrivate(),key.getPublic()),false);
    }
    private GroupRestoreGuard.OwnerChannel verified(KeyPair key){
        return new GroupRestoreGuard.OwnerChannel(owner,
            FingerprintVerifier.spkiFingerprint(key.getPublic()),true,true);
    }
    @Test void diskMetadataNeverAutomaticallyUnlocksGroupWithoutExplicitVerifiedOwner()
        throws Exception{
        KeyPair key=keys();
        var file=dir.resolve("group.dpapi");
        var store=new ProtectedGroupStore(file);
        var first=signed(key,1,new byte[32]);
        var trusted=Map.of(owner,key.getPublic());
        store.upsert(first,trusted);
        var loaded=store.load(trusted).getFirst();
        assertTrue(loaded.stale());
        var guard=new GroupRestoreGuard(loaded,key.getPublic(),store);
        assertEquals(GroupRestoreGuard.State.STALE,guard.state());
        assertFalse(guard.canSend());
        assertThrows(IllegalStateException.class,()->guard.revalidate(
            first,new GroupRestoreGuard.OwnerChannel(owner,
                FingerprintVerifier.spkiFingerprint(key.getPublic()),false,true)));
        assertFalse(guard.canSend());
        guard.beginOwnerResync();
        assertEquals(GroupRestoreGuard.State.PENDING_RESYNC,guard.state());
        assertFalse(guard.canSend());
        guard.revalidate(first,verified(key));
        assertTrue(guard.canSend());
        guard.ownerDisconnected();
        assertEquals(GroupRestoreGuard.State.STALE,guard.state());
        assertFalse(guard.canSend(),"Owner offline must pause new group sends");
        guard.close();
        assertEquals(GroupRestoreGuard.State.CLOSED,guard.state());
        assertThrows(IllegalStateException.class,guard::beginOwnerResync);
    }
    @Test void restoredOlderCiphertextCannotReplayStaleRosterOrSkipHashChain()
        throws Exception {
        KeyPair key=keys();Path file=dir.resolve("rollback.dpapi");
        var store=new ProtectedGroupStore(file);
        var trusted=Map.of(owner,key.getPublic());
        var one=signed(key,1,new byte[32]);
        var two=signed(key,2,GroupCanonicalBytes.sha256(
            GroupCanonicalBytes.roster(one.roster())));
        var three=signed(key,3,GroupCanonicalBytes.sha256(
            GroupCanonicalBytes.roster(two.roster())));
        store.upsert(one,trusted);
        byte[] backup=Files.readAllBytes(file);
        store.upsert(two,trusted);
        assertEquals(2,store.load(trusted).getFirst().roster().revision());
        Files.write(file,backup); // test-controlled old DPAPI ciphertext on same user
        var guard=new GroupRestoreGuard(store.load(trusted).getFirst(),key.getPublic(),store);
        assertEquals(GroupRestoreGuard.State.STALE,guard.state());
        guard.beginOwnerResync();
        assertThrows(IllegalStateException.class,()->guard.revalidate(three,verified(key)),
            "Owner cannot jump revision without intermediate signed hash chain");
        assertFalse(guard.canSend());
        guard.revalidate(two,verified(key));
        assertTrue(guard.canSend());
        assertEquals(2,store.load(trusted).getFirst().roster().revision());
        assertThrows(IllegalStateException.class,()->guard.revalidate(one,verified(key)),
            "Once revision 2 committed, a signed old revision 1 is still rollback");
        assertEquals(2,guard.revision());
    }
    @Test void changedOwnerKeyOrWrongTlsIdentityCannotReauthorizeDisk()
        throws Exception{
        KeyPair key=keys(),different=keys();
        Path file=dir.resolve("owner.dpapi");
        var store=new ProtectedGroupStore(file);
        var signed=signed(key,1,new byte[32]);
        var trusted=Map.of(owner,key.getPublic());
        store.upsert(signed,trusted);
        var guard=new GroupRestoreGuard(store.load(trusted).getFirst(),key.getPublic(),store);
        guard.beginOwnerResync();
        assertThrows(IllegalStateException.class,()->guard.revalidate(signed,
            new GroupRestoreGuard.OwnerChannel(UUID.randomUUID(),
                FingerprintVerifier.spkiFingerprint(key.getPublic()),true,true)));
        assertThrows(IllegalStateException.class,()->guard.revalidate(signed,
            new GroupRestoreGuard.OwnerChannel(owner,
                FingerprintVerifier.spkiFingerprint(different.getPublic()),true,true)));
        assertThrows(IllegalStateException.class,()->guard.revalidate(signed,
            new GroupRestoreGuard.OwnerChannel(owner,
                FingerprintVerifier.spkiFingerprint(key.getPublic()),true,false)));
        assertFalse(guard.canSend());
        assertEquals(GroupRestoreGuard.State.PENDING_RESYNC,guard.state());
    }
}
