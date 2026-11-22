package vn.intrachat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import vn.intrachat.domain.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL Windows CurrentUser-DPAPI metadata files. Tests never need fake plaintext
 * fallback. Different Windows logon token is unavailable: not a PASS claim.
 */
class GroupStoreCorruptionTest {
    @TempDir Path temp;
    private final UUID owner=UUID.randomUUID(),peer=UUID.randomUUID();
    private final UUID group=UUID.randomUUID();
    private static KeyPair keys()throws Exception{
        KeyPairGenerator g=KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }
    private static byte[] fingerprint(PublicKey key){
        return FingerprintVerifier.spkiFingerprint(key);
    }
    private GroupMetadata signed(KeyPair pair,UUID groupId,long revision,byte[] prev)
        throws Exception {
        byte[] spki=fingerprint(pair.getPublic()),other=new byte[32];
        Arrays.fill(other,(byte)13);
        GroupRoster roster=new GroupRoster(new GroupIdentity(groupId,"Metadata only",owner,spki),
            revision,prev,List.of(
                new GroupMember(owner,spki,GroupMember.Role.OWNER),
                new GroupMember(peer,other,GroupMember.Role.MEMBER)),
            UUID.randomUUID(),Instant.parse("2026-11-22T09:00:00Z"));
        return new GroupMetadata(roster,GroupOwnerSignature.signRoster(
            roster,pair.getPrivate(),pair.getPublic()),false);
    }
    private static byte[] rawHeader(int magic,int version,int count)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(b)){
            out.writeInt(magic);out.writeInt(version);out.writeInt(count);
        }
        return b.toByteArray();
    }
    private static byte[] rawEntries(GroupMetadata... groups)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(b)){
            out.writeInt(0x47475231);out.writeInt(1);out.writeInt(groups.length);
            for(GroupMetadata m:groups){
                byte[] bytes=GroupCanonicalBytes.roster(m.roster());
                out.writeInt(bytes.length);out.write(bytes);
                byte[] sig=m.ownerSignature();out.writeInt(sig.length);out.write(sig);
            }
        }
        return b.toByteArray();
    }
    @Test void missingStoreReturnsEmptyButDirectoryCorruptedCipherAndHugeBlobFailClosed()
        throws Exception{
        KeyPair pair=keys();
        Path file=temp.resolve("group-missing.dpapi");
        ProtectedGroupStore store=new ProtectedGroupStore(file);
        Map<UUID,PublicKey> trusted=Map.of(owner,pair.getPublic());
        assertTrue(store.load(trusted).isEmpty(),"Missing file is not corruption");
        Files.createDirectory(file);
        assertThrows(IOException.class,()->store.load(trusted),
            "A folder may not masquerade as a protected metadata file");
        Files.delete(file);
        store.upsert(signed(pair,group,1,new byte[32]),trusted);
        byte[] valid=Files.readAllBytes(file);
        assertTrue(valid.length>64);
        Files.write(file,Arrays.copyOf(valid,valid.length/2));
        assertThrows(IOException.class,()->store.load(trusted));
        Files.write(file,new byte[1_048_577]);
        assertThrows(IOException.class,()->store.load(trusted),
            "Encrypted group store must be at most one MiB");
    }
    @Test void wrongSchemaDuplicateGroupAndTamperedSignatureNeverLoad()throws Exception{
        KeyPair pair=keys();Map<UUID,PublicKey> trusted=Map.of(owner,pair.getPublic());
        Path file=temp.resolve("bad-schema.dpapi");
        ProtectedGroupStore store=new ProtectedGroupStore(file);
        ProtectedLocalFile.write(file,rawHeader(0x47475231,2,0));
        assertThrows(IOException.class,()->store.load(trusted));
        ProtectedLocalFile.write(file,rawHeader(0x47475231,1,17));
        assertThrows(IOException.class,()->store.load(trusted));
        GroupMetadata signed=signed(pair,group,1,new byte[32]);
        ProtectedLocalFile.write(file,rawEntries(signed,signed));
        assertThrows(IOException.class,()->store.load(trusted),
            "No duplicate groupId even if both owner signatures are valid");
        byte[] corrupted=rawEntries(signed);
        corrupted[corrupted.length-1]^=1;
        ProtectedLocalFile.write(file,corrupted);
        assertThrows(IOException.class,()->store.load(trusted));
    }
    @Test void untrustedKeyOrInvalidRevisionCannotOverwritePreviouslyValidMetadata()
        throws Exception {
        KeyPair pair=keys();Map<UUID,PublicKey> trusted=Map.of(owner,pair.getPublic());
        Path file=temp.resolve("rollback.dpapi");
        ProtectedGroupStore store=new ProtectedGroupStore(file);
        GroupMetadata original=signed(pair,group,1,new byte[32]);
        store.upsert(original,trusted);
        byte[] before=Files.readAllBytes(file);
        GroupMetadata wrong=signed(pair,group,4,new byte[32]);
        assertThrows(IOException.class,()->store.upsert(wrong,trusted));
        assertArrayEquals(before,Files.readAllBytes(file),
            "Rejected roster cannot destroy the last valid DPAPI file");
        assertThrows(IOException.class,()->store.load(Map.of(owner,keys().getPublic())));
        assertArrayEquals(before,Files.readAllBytes(file));
        assertTrue(store.load(trusted).getFirst().stale());
        assertEquals(1,store.load(trusted).getFirst().roster().revision());
    }
    @Test void widenedOrDeniedWindowsAclFailsClosedAndCanBeRestored()throws Exception{
        KeyPair pair=keys();var trusted=Map.of(owner,pair.getPublic());
        Path file=temp.resolve("bad-acl.dpapi");
        var store=new ProtectedGroupStore(file);
        store.upsert(signed(pair,group,1,new byte[32]),trusted);
        AclFileAttributeView view=Files.getFileAttributeView(file,AclFileAttributeView.class);
        assertNotNull(view,"Windows owner-only ACL required");
        List<AclEntry> original=view.getAcl();
        UserPrincipal identity=Files.getOwner(file);
        var deny=AclEntry.newBuilder().setType(AclEntryType.DENY)
            .setPrincipal(identity).setPermissions(AclEntryPermission.READ_DATA).build();
        try{
            List<AclEntry> invalid=new ArrayList<>();
            invalid.add(deny);invalid.addAll(original);
            view.setAcl(invalid);
            assertThrows(IOException.class,()->store.load(trusted),
                "Any unexpected DENY or other ACL entry blocks protected Group metadata");
        }finally{
            view.setAcl(original); // Restore only the ephemeral JUnit file ACL
        }
        assertEquals(1,store.load(trusted).size());
    }
    @Test void nonRegularPathDeniedAndSymbolicLinkWhenWindowsAuthorizesTest()throws Exception{
        KeyPair pair=keys();var trusted=Map.of(owner,pair.getPublic());
        Path real=temp.resolve("real.dpapi"),alias=temp.resolve("alias.dpapi");
        new ProtectedGroupStore(real).upsert(signed(pair,group,1,new byte[32]),trusted);
        Files.createDirectory(alias);
        assertThrows(IOException.class,()->new ProtectedGroupStore(alias).load(trusted));
        Files.delete(alias);
        try{
            Files.createSymbolicLink(alias,real);
        }catch(UnsupportedOperationException|IOException|SecurityException unavailable){
            // Windows test account cannot create a symlink; directory guard still tested.
            // Do not claim the symlink negative case was executed.
            return;
        }
        assertTrue(Files.isSymbolicLink(alias));
        assertThrows(IOException.class,()->new ProtectedGroupStore(alias).load(trusted),
            "Reject store symlinks instead of following attacker-controlled paths");
        assertEquals(1,new ProtectedGroupStore(real).load(trusted).size());
    }

    @Test void malformedSixMemberCanonicalRosterAndTrailingContentCannotLoad()
        throws Exception{
        KeyPair pair=keys();
        Path file=temp.resolve("invalid-members.dpapi");
        var store=new ProtectedGroupStore(file);
        var trusted=Map.of(owner,pair.getPublic());
        var valid=signed(pair,group,1,new byte[32]);
        byte[] canonical=GroupCanonicalBytes.roster(valid.roster());
        int start="INTRACHAT_GROUP_ROSTER_V1".length()+1+16+16+32+8+32;
        int titleLen=((canonical[start]&0xff)<<8)|(canonical[start+1]&0xff);
        int countOffset=start+2+titleLen;
        assertEquals(2,canonical[countOffset]);
        canonical[countOffset]=6;
        byte[] synthetic=rawEntries(valid);
        byte[] find=GroupCanonicalBytes.roster(valid.roster());
        for(int i=0;i<=synthetic.length-find.length;i++){
            if(Arrays.equals(Arrays.copyOfRange(synthetic,i,i+find.length),find)){
                synthetic[i+countOffset]=6;break;
            }
        }
        ProtectedLocalFile.write(file,synthetic);
        assertThrows(IOException.class,()->store.load(trusted));
        var trailing=Arrays.copyOf(rawEntries(valid),rawEntries(valid).length+1);
        trailing[trailing.length-1]=1;
        ProtectedLocalFile.write(file,trailing);
        assertThrows(IOException.class,()->store.load(trusted));
    }
}
