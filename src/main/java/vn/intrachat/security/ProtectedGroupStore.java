package vn.intrachat.security;

import vn.intrachat.domain.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;

/**
 * Windows CurrentUser-DPAPI store of signed Group METADATA ONLY.
 * No chat bodies, drafts, grants or recipient receipts can be serialized by this API.
 *
 * Trust map MUST come from authenticated M2 pinned peer identities or the local
 * DeviceIdentityStore, never from untrusted Group packet fields.
 * Loaded groups are always STALE until online owner and verified peers reauthorize.
 */
public final class ProtectedGroupStore {
    private static final int MAGIC=0x47475231;  // GGR1
    private static final int VERSION=1;
    private static final int MAX_GROUPS=16;
    private final Path file;
    public ProtectedGroupStore(Path file){
        this.file=Objects.requireNonNull(file,"file").toAbsolutePath().normalize();
    }
    public Path path(){return file;}

    public synchronized List<GroupMetadata> load(Map<UUID,PublicKey> trustedOwnerKeys)throws IOException {
        Objects.requireNonNull(trustedOwnerKeys,"trustedOwnerKeys");
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return List.of();
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Protected Group metadata must be a real regular file");
        if(Files.size(file)>1_048_576)
            throw new IOException("Protected Group ciphertext exceeds one MiB");
        checkAcl();
        byte[] clear=ProtectedLocalFile.read(file);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(clear))){
            if(clear.length>524_288)
                throw new IOException("Protected Group metadata exceeds permitted plaintext bound");
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unsupported protected Group store format");
            int count=in.readInt();
            if(count<0||count>MAX_GROUPS)throw new IOException("Invalid protected Group count");
            List<GroupMetadata> loaded=new ArrayList<>(count);
            Set<UUID> ids=new HashSet<>();
            for(int i=0;i<count;i++){
                byte[] bytes=readBlob(in,4096);
                byte[] sig=readBlob(in,128);
                if(sig.length<64)throw new IOException("Invalid owner signature length");
                GroupRoster roster=GroupCanonicalBytes.parseRoster(bytes);
                PublicKey ownerKey=trustedOwnerKeys.get(roster.identity().ownerDeviceId());
                if(ownerKey==null||!GroupOwnerSignature.verifyRoster(roster,sig,ownerKey))
                    throw new IOException("Owner Group signature/pin verification failed");
                if(!ids.add(roster.identity().groupId()))
                    throw new IOException("Duplicate protected Group ID");
                loaded.add(new GroupMetadata(roster,sig,true)); // NEVER restore as ACTIVE
            }
            if(in.available()!=0)throw new IOException("Trailing protected Group data");
            return List.copyOf(loaded);
        }catch(IllegalArgumentException e){throw new IOException("Invalid protected Group data",e);}
        finally{Arrays.fill(clear,(byte)0);}
    }

    /**
     * A committed owner-signed roster may replace N only with N+1 and correct
     * previous hash; owner-signature and trusted key must validate first.
     * Writes are DPAPI-encrypted atomically before caller can publish the commit.
     */
    public synchronized void upsert(GroupMetadata candidate,Map<UUID,PublicKey> trustedOwnerKeys)throws IOException {
        Objects.requireNonNull(candidate,"candidate");
        Objects.requireNonNull(trustedOwnerKeys,"trustedOwnerKeys");
        PublicKey ownerKey=trustedOwnerKeys.get(candidate.roster().identity().ownerDeviceId());
        if(ownerKey==null)throw new IOException("Group owner public key not independently trusted");
        List<GroupMetadata> groups=new ArrayList<>(load(trustedOwnerKeys));
        int position=-1;
        for(int i=0;i<groups.size();i++){
            if(groups.get(i).roster().identity().groupId().equals(candidate.roster().identity().groupId())){
                position=i;break;
            }
        }
        GroupMetadata prior=position<0?null:groups.get(position);
        if(!GroupOwnerSignature.acceptCommit(candidate,prior,ownerKey))
            throw new IOException("Group roster signature, continuity or revision rejected");
        // The stale marker is not persisted. It reflects online session state at runtime only.
        GroupMetadata stored=new GroupMetadata(candidate.roster(),candidate.ownerSignature(),true);
        if(position>=0)groups.set(position,stored);
        else{
            if(groups.size()>=MAX_GROUPS)throw new IOException("Protected Group directory full");
            groups.add(stored);
        }
        groups.sort(Comparator.comparing(g->g.roster().identity().groupId().toString()));
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(buffer)){
            out.writeInt(MAGIC);out.writeInt(VERSION);out.writeInt(groups.size());
            for(GroupMetadata g:groups){
                writeBlob(out,GroupCanonicalBytes.roster(g.roster()));
                writeBlob(out,g.ownerSignature());
            }
        }
        byte[] clear=buffer.toByteArray();
        try{
            if(clear.length>524_288)throw new IOException("Group metadata plaintext size exceeded");
            ProtectedLocalFile.write(file,clear);
        }finally{Arrays.fill(clear,(byte)0);}
    }
    private static void writeBlob(DataOutputStream out,byte[] bytes)throws IOException{
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static byte[] readBlob(DataInputStream in,int max)throws IOException{
        int length=in.readInt();
        if(length<1||length>max)throw new IOException("Protected group component over size limit");
        byte[] bytes=new byte[length];in.readFully(bytes);return bytes;
    }
    private void checkAcl()throws IOException{
        AclFileAttributeView view=Files.getFileAttributeView(file,AclFileAttributeView.class);
        if(view==null)throw new IOException("Group DPAPI file owner ACL unavailable");
        UserPrincipal owner=Files.getOwner(file);
        List<AclEntry> acl=view.getAcl();
        if(acl.isEmpty())throw new IOException("Empty protected Group ACL");
        for(AclEntry entry:acl){
            if(!entry.principal().equals(owner)||entry.type()!=AclEntryType.ALLOW)
                throw new IOException("Protected Group ACL permits unexpected identity");
        }
    }
}
