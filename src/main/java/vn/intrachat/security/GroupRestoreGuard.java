package vn.intrachat.security;

import vn.intrachat.domain.*;
import java.io.IOException;
import java.security.*;
import java.util.*;

/**
 * Local-only post-restart trust barrier. A valid DPAPI ciphertext is merely
 * metadata, never permission to send Group data or a reason to reconnect.
 *
 * An explicit owner re-sync on the SAME independently pinned TLS identity and
 * negotiated Group capability is required. Owner updates must be sequentially
 * signed and atomically saved before the local snapshot becomes ACTIVE.
 *
 * No chat text, drafts, receipts or grants are held by this class.
 */
public final class GroupRestoreGuard implements AutoCloseable {
    public enum State { STALE, PENDING_RESYNC, ACTIVE, CLOSED }
    public record OwnerChannel(UUID verifiedOwnerId,byte[] verifiedOwnerSpkiSha256,
                               boolean tlsReady,boolean groupCapability) {
        public OwnerChannel {
            Objects.requireNonNull(verifiedOwnerId);
            if(verifiedOwnerSpkiSha256==null||verifiedOwnerSpkiSha256.length!=32)
                throw new IllegalArgumentException("Owner TLS pin must be SHA256");
            verifiedOwnerSpkiSha256=verifiedOwnerSpkiSha256.clone();
        }
        @Override public byte[] verifiedOwnerSpkiSha256(){return verifiedOwnerSpkiSha256.clone();}
    }
    private final PublicKey pinnedOwnerKey;
    private final ProtectedGroupStore store;
    private GroupMetadata current;
    private State state=State.STALE;

    public GroupRestoreGuard(GroupMetadata loaded,PublicKey pinnedOwnerKey,
                             ProtectedGroupStore store){
        this.current=Objects.requireNonNull(loaded);
        this.pinnedOwnerKey=Objects.requireNonNull(pinnedOwnerKey);
        this.store=Objects.requireNonNull(store);
        if(!loaded.stale()||!GroupOwnerSignature.verifyRoster(
            loaded.roster(),loaded.ownerSignature(),pinnedOwnerKey))
            throw new IllegalArgumentException("A verified STALE metadata snapshot is required");
    }
    public synchronized State state(){return state;}
    public synchronized boolean canSend(){return state==State.ACTIVE;}
    public synchronized long revision(){return current.roster().revision();}
    public synchronized void beginOwnerResync(){
        requireNotClosed();
        state=State.PENDING_RESYNC; // user action only; no sockets are opened
    }
    public synchronized void ownerDisconnected(){
        if(state!=State.CLOSED)state=State.STALE; // grant issuing remains disabled
    }
    public synchronized void revalidate(GroupMetadata ownerSigned,OwnerChannel authenticated)
        throws IOException{
        requireNotClosed();
        if(state!=State.PENDING_RESYNC && state!=State.ACTIVE)
            throw new IllegalStateException("Explicit owner revalidation is required");
        Objects.requireNonNull(ownerSigned);Objects.requireNonNull(authenticated);
        GroupRoster old=current.roster(),next=ownerSigned.roster();
        byte[] trustedSpki=FingerprintVerifier.spkiFingerprint(pinnedOwnerKey);
        if(!authenticated.tlsReady()||!authenticated.groupCapability()
            ||!authenticated.verifiedOwnerId().equals(old.identity().ownerDeviceId())
            ||!MessageDigest.isEqual(authenticated.verifiedOwnerSpkiSha256(),trustedSpki)
            ||!MessageDigest.isEqual(old.identity().ownerSpkiSha256(),trustedSpki)
            ||ownerSigned.stale()
            ||!GroupOwnerSignature.verifyRoster(next,ownerSigned.ownerSignature(),pinnedOwnerKey)
            ||!next.identity().groupId().equals(old.identity().groupId()))
            throw new IllegalStateException("Owner TLS identity, capability or signed roster rejected");

        if(next.revision()==old.revision()){
            // Same authoritative snapshot. A signature alone with the same
            // revision but altered title/members/event is NOT revalidation.
            if(!MessageDigest.isEqual(GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(old)),
                GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(next))))
                throw new IllegalStateException("Conflicting roster at the same revision");
        } else {
            if(!GroupOwnerSignature.acceptCommit(ownerSigned,current,pinnedOwnerKey))
                throw new IllegalStateException("Missing signed roster chain or rollback");
            // Durable write MUST finish before enabling Group send.
            store.upsert(ownerSigned,Map.of(old.identity().ownerDeviceId(),pinnedOwnerKey));
        }
        current=new GroupMetadata(next,ownerSigned.ownerSignature(),false);
        state=State.ACTIVE;
    }
    private void requireNotClosed(){
        if(state==State.CLOSED)throw new IllegalStateException("Group restoration closed");
    }
    @Override public synchronized void close(){
        state=State.CLOSED;current=null;
    }
}
