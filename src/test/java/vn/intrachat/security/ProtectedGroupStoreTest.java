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

class ProtectedGroupStoreTest {
    @TempDir Path temp;
    private static final UUID OWNER=GroupCanonicalBytesTest.OWNER;
    private static final UUID PEER=GroupCanonicalBytesTest.PEER;
    private static byte[] pin(int n){byte[] b=new byte[32];Arrays.fill(b,(byte)n);return b;}
    private static KeyPair pair()throws Exception{
        var gen=KeyPairGenerator.getInstance("EC");gen.initialize(new ECGenParameterSpec("secp256r1"));return gen.generateKeyPair();
    }
    private static GroupMetadata group(KeyPair owner,UUID groupId,long revision,byte[] prev)throws Exception {
        byte[] hash=FingerprintVerifier.spkiFingerprint(owner.getPublic());
        var identity=new GroupIdentity(groupId,"Nhóm thử nghiệm",OWNER,hash);
        var roster=new GroupRoster(identity,revision,prev,List.of(
            new GroupMember(OWNER,hash,GroupMember.Role.OWNER),
            new GroupMember(PEER,pin(9),GroupMember.Role.MEMBER)),
            UUID.randomUUID(),Instant.parse("2026-11-22T12:00:00Z"));
        return new GroupMetadata(roster,GroupOwnerSignature.signRoster(roster,owner.getPrivate(),owner.getPublic()),false);
    }
    @Test void encryptedMetadataRestartsStaleWithoutChat() throws Exception {
        KeyPair owner=pair();
        var file=temp.resolve("security").resolve("groups.dpapi");
        var store=new ProtectedGroupStore(file);
        assertEquals(List.of(),store.load(Map.of(OWNER,owner.getPublic())));
        var group=group(owner,UUID.randomUUID(),1,new byte[32]);
        store.upsert(group,Map.of(OWNER,owner.getPublic()));
        assertTrue(Files.exists(file));
        byte[] raw=Files.readAllBytes(file);
        assertFalse(new String(raw,java.nio.charset.StandardCharsets.ISO_8859_1).contains("Nhóm thử nghiệm"));
        assertFalse(new String(raw,java.nio.charset.StandardCharsets.ISO_8859_1).contains("private message content"));
        var restarted=new ProtectedGroupStore(file);
        var loaded=restarted.load(Map.of(OWNER,owner.getPublic()));
        assertEquals(1,loaded.size());
        assertTrue(loaded.getFirst().stale());
        assertEquals(group.roster(),loaded.getFirst().roster());
        assertArrayEquals(group.ownerSignature(),loaded.getFirst().ownerSignature());
    }
    @Test void rejectsUntrustedOwnerCorruptionAndRollbackOnUpdate() throws Exception {
        KeyPair owner=pair();
        var file=temp.resolve("groups.dpapi");
        var store=new ProtectedGroupStore(file);
        UUID groupId=UUID.randomUUID();
        var first=group(owner,groupId,1,new byte[32]);
        store.upsert(first,Map.of(OWNER,owner.getPublic()));
        assertThrows(Exception.class,()->store.load(Map.of(OWNER,pair().getPublic())));
        assertThrows(Exception.class,()->store.upsert(first,Map.of(OWNER,owner.getPublic())));
        var next=group(owner,groupId,2,GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(first.roster())));
        store.upsert(next,Map.of(OWNER,owner.getPublic()));
        assertEquals(2,store.load(Map.of(OWNER,owner.getPublic())).getFirst().roster().revision());
        byte[] tampered=Files.readAllBytes(file);
        tampered[tampered.length-1]^=1;Files.write(file,tampered);
        assertThrows(Exception.class,()->store.load(Map.of(OWNER,owner.getPublic())));
    }
    @Test void multipleGroupsAreBoundedAndAllReadAsUntrustedStale()throws Exception {
        KeyPair owner=pair();
        var file=temp.resolve("groups.dpapi");var store=new ProtectedGroupStore(file);
        for(int i=0;i<16;i++)store.upsert(group(owner,UUID.randomUUID(),1,new byte[32]),Map.of(OWNER,owner.getPublic()));
        assertEquals(16,store.load(Map.of(OWNER,owner.getPublic())).size());
        assertThrows(Exception.class,()->store.upsert(group(owner,UUID.randomUUID(),1,new byte[32]),Map.of(OWNER,owner.getPublic())));
    }
}
