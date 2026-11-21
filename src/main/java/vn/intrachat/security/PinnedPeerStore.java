package vn.intrachat.security;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Protected verified peer pin database, distinct from RAM-only message history. */
public final class PinnedPeerStore {
    private static final int MAGIC=0x50494e32;
    private static final int MAX_PEERS=256;
    private final Path file;
    public PinnedPeerStore(Path file){this.file=Objects.requireNonNull(file).toAbsolutePath().normalize();}
    public Path path(){return file;}
    public synchronized boolean matches(UUID deviceId,byte[] fingerprint)throws IOException{
        Objects.requireNonNull(deviceId);
        byte[] stored=load().get(deviceId);
        return FingerprintVerifier.sameFingerprint(stored,fingerprint);
    }
    public synchronized boolean known(UUID deviceId)throws IOException{
        return load().containsKey(Objects.requireNonNull(deviceId));
    }
    public synchronized void pinNew(UUID deviceId,byte[] fingerprint)throws IOException{
        Objects.requireNonNull(deviceId);
        if(fingerprint==null||fingerprint.length!=32)throw new IllegalArgumentException("SPKI fingerprint length");
        Map<UUID,byte[]> pins=load();
        byte[] existing=pins.get(deviceId);
        if(existing!=null){
            if(FingerprintVerifier.sameFingerprint(existing,fingerprint))return;
            throw new IllegalStateException("Peer key changed; explicit revoke and re-pair required");
        }
        if(pins.size()>=MAX_PEERS)throw new IOException("Peer trust store capacity exceeded");
        pins.put(deviceId,fingerprint.clone());
        save(pins);
    }
    /** Explicit user revocation only; never called automatically for changed key. */
    public synchronized void revoke(UUID deviceId)throws IOException{
        Map<UUID,byte[]> pins=load();
        if(pins.remove(Objects.requireNonNull(deviceId))!=null)save(pins);
    }
    private Map<UUID,byte[]> load()throws IOException{
        Map<UUID,byte[]> entries=new LinkedHashMap<>();
        if(!Files.exists(file))return entries;
        byte[] clear=ProtectedLocalFile.read(file);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(clear))){
            if(in.readInt()!=MAGIC)throw new IOException("Invalid trust store magic");
            int count=in.readInt();
            if(count<0||count>MAX_PEERS)throw new IOException("Invalid trust store count");
            for(int i=0;i<count;i++){
                UUID id=new UUID(in.readLong(),in.readLong());
                byte[] pin=new byte[32];in.readFully(pin);
                if(entries.putIfAbsent(id,pin)!=null)throw new IOException("Duplicate peer trust record");
            }
            if(in.available()!=0)throw new IOException("Trailing trust store data");
            return entries;
        }finally{Arrays.fill(clear,(byte)0);}
    }
    private void save(Map<UUID,byte[]> entries)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeInt(entries.size());
            for(var e:entries.entrySet()){
                out.writeLong(e.getKey().getMostSignificantBits());
                out.writeLong(e.getKey().getLeastSignificantBits());
                out.write(e.getValue());
            }
        }
        byte[] clear=bytes.toByteArray();
        try{ProtectedLocalFile.write(file,clear);}
        finally{Arrays.fill(clear,(byte)0);}
    }
}
