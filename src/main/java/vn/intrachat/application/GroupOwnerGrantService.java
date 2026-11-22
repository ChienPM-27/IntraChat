package vn.intrachat.application;

import vn.intrachat.domain.*;
import vn.intrachat.security.*;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Owner-side control only: signs membership-bound per-message recipient tickets.
 * Never receives or forwards message text. This class has NO network listener;
 * the caller must verify actual TLS identity/capability and explicit invite consent.
 *
 * Experimental service for Spec 004, disabled in Messenger Live pending full
 * negotiated transport and independent security review.
 */
public final class GroupOwnerGrantService implements AutoCloseable {
    public record SignedGrant(GroupGrant grant,byte[] signature) {
        public SignedGrant {
            Objects.requireNonNull(grant); Objects.requireNonNull(signature);
            if(signature.length<64||signature.length>128)
                throw new IllegalArgumentException("Invalid signed grant size");
            signature=signature.clone();
        }
        @Override public byte[] signature(){return signature.clone();}
    }
    private final DeviceIdentity localOwner;
    private final SecureRandom random=new SecureRandom();
    private GroupMetadata current;
    private boolean closed;

    public GroupOwnerGrantService(DeviceIdentity localOwner){
        this.localOwner=Objects.requireNonNull(localOwner);
        GroupIdentity.requireId(localOwner.deviceId(),"owner deviceId");
    }
    /** Verified owner-signed commit; N+1 only, never rollback or unauthenticated alias. */
    public synchronized void activate(GroupMetadata committed)throws IOException{
        if(closed)throw new IOException("Group owner service closed");
        Objects.requireNonNull(committed,"committed");
        GroupIdentity id=committed.roster().identity();
        if(!id.ownerDeviceId().equals(localOwner.deviceId())
            ||!java.security.MessageDigest.isEqual(id.ownerSpkiSha256(),localOwner.fingerprint()))
            throw new IOException("Group creator identity does not match local authenticated device");
        if(current!=null&&!current.roster().identity().groupId().equals(id.groupId()))
            throw new IOException("Owner authority is scoped to one group ID");
        if(!GroupOwnerSignature.acceptCommit(committed,current,localOwner.keyPair().getPublic()))
            throw new IOException("Owner roster signature/monotonic revision rejected");
        current=committed;
    }
    /** Grant always binds to ALL committed group members other than sender. */
    public synchronized SignedGrant issue(UUID authenticatedSender,UUID messageId,
                              Collection<UUID> requestedRecipients,Instant now)throws IOException{
        if(closed||current==null)throw new IOException("Group owner is unavailable");
        GroupIdentity.requireId(authenticatedSender,"authenticatedSender");
        GroupIdentity.requireId(messageId,"messageId");
        Objects.requireNonNull(requestedRecipients,"requestedRecipients");
        Objects.requireNonNull(now,"now");
        if(now.isBefore(Instant.EPOCH))throw new IOException("Invalid issue time");
        GroupRoster roster=current.roster();
        Set<UUID> active=new HashSet<>();
        for(GroupMember m:roster.members())active.add(m.deviceId());
        if(!active.contains(authenticatedSender))throw new IOException("Sender not in committed group roster");
        Set<UUID> exact=new HashSet<>(active);
        exact.remove(authenticatedSender);
        if(requestedRecipients.size()!=exact.size()
            ||!requestedRecipients.equals(exact) && !new HashSet<>(requestedRecipients).equals(exact))
            throw new IOException("Requested recipient set differs from committed group roster");
        if(requestedRecipients.size()!=new HashSet<>(requestedRecipients).size())
            throw new IOException("Duplicate group recipient");
        byte[] nonce=new byte[16];random.nextBytes(nonce);
        try {
            GroupGrant ticket=new GroupGrant(roster.identity().groupId(),messageId,localOwner.deviceId(),
                localOwner.fingerprint(),roster.revision(),
                GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster)),
                authenticatedSender,new ArrayList<>(exact),now,now.plusSeconds(10),nonce);
            byte[] signature=GroupOwnerSignature.signGrant(ticket,
                localOwner.keyPair().getPrivate(),localOwner.keyPair().getPublic());
            return new SignedGrant(ticket,signature);
        }catch(GeneralSecurityException|IllegalArgumentException e){
            throw new IOException("Cannot authorize Group sender",e);
        }finally{Arrays.fill(nonce,(byte)0);}
    }
    public synchronized long committedRevision(){return current==null?0:current.roster().revision();}
    public synchronized UUID groupId(){return current==null?null:current.roster().identity().groupId();}
    @Override public synchronized void close(){closed=true;current=null;}
}
