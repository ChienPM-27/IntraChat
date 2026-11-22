package vn.intrachat.domain;

import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;

/**
 * An owner-authorized message-specific recipient set, NOT a reusable group token.
 * Actual authenticated TLS session and current signed roster must also be checked.
 */
public record GroupGrant(UUID groupId,UUID messageId,UUID ownerDeviceId,byte[] ownerSpkiSha256,
                         long revision,byte[] rosterHash,UUID senderId,List<UUID> recipientIds,
                         Instant issuedAt,Instant expiresAt,byte[] nonce) {
    public GroupGrant {
        GroupIdentity.requireId(groupId,"groupId");
        GroupIdentity.requireId(messageId,"messageId");
        GroupIdentity.requireId(ownerDeviceId,"ownerDeviceId");
        GroupIdentity.requireId(senderId,"senderId");
        if(ownerSpkiSha256==null||ownerSpkiSha256.length!=32)
            throw new IllegalArgumentException("Invalid owner SPKI");
        if(rosterHash==null||rosterHash.length!=32)
            throw new IllegalArgumentException("Invalid roster hash");
        if(nonce==null||nonce.length!=16)throw new IllegalArgumentException("Invalid nonce length");
        if(revision<1)throw new IllegalArgumentException("Grant revision must be committed");
        Objects.requireNonNull(issuedAt,"issuedAt");Objects.requireNonNull(expiresAt,"expiresAt");
        if(issuedAt.isBefore(Instant.EPOCH))throw new IllegalArgumentException("Negative issue time");
        long duration;
        try{duration=java.time.Duration.between(issuedAt,expiresAt).toMillis();}
        catch(ArithmeticException e){throw new IllegalArgumentException("Invalid grant lifetime",e);}
        if(duration<1 || duration>10_000 || !expiresAt.isAfter(issuedAt))
            throw new IllegalArgumentException("Grant validity must be 1..10000ms");
        Objects.requireNonNull(recipientIds,"recipientIds");
        if(recipientIds.isEmpty()||recipientIds.size()>4)
            throw new IllegalArgumentException("Grant must target 1..4 recipients");
        List<UUID> ordered=new ArrayList<>(recipientIds);
        for(UUID id:ordered) {
            GroupIdentity.requireId(id,"recipient");
            if(id.equals(senderId))throw new IllegalArgumentException("Sender cannot be recipient");
        }
        ordered.sort((a,b)->{
            int x=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());
            return x!=0?x:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());
        });
        if(new HashSet<>(ordered).size()!=ordered.size())throw new IllegalArgumentException("Duplicate recipients");
        recipientIds=List.copyOf(ordered);
        ownerSpkiSha256=ownerSpkiSha256.clone();
        rosterHash=rosterHash.clone();
        nonce=nonce.clone();
    }
    @Override public byte[] ownerSpkiSha256(){return ownerSpkiSha256.clone();}
    @Override public byte[] rosterHash(){return rosterHash.clone();}
    @Override public byte[] nonce(){return nonce.clone();}
    @Override public boolean equals(Object obj) {
        return this==obj || obj instanceof GroupGrant g && groupId.equals(g.groupId)
            && messageId.equals(g.messageId) && ownerDeviceId.equals(g.ownerDeviceId)
            && MessageDigest.isEqual(ownerSpkiSha256,g.ownerSpkiSha256)
            && revision==g.revision && MessageDigest.isEqual(rosterHash,g.rosterHash)
            && senderId.equals(g.senderId) && recipientIds.equals(g.recipientIds)
            && issuedAt.equals(g.issuedAt) && expiresAt.equals(g.expiresAt)
            && MessageDigest.isEqual(nonce,g.nonce);
    }
    @Override public int hashCode(){
        return Objects.hash(groupId,messageId,ownerDeviceId,Arrays.hashCode(ownerSpkiSha256),
            revision,Arrays.hashCode(rosterHash),senderId,recipientIds,issuedAt,expiresAt,Arrays.hashCode(nonce));
    }
    @Override public String toString(){return "GroupGrant[groupId="+groupId+",messageId="+messageId+
            ",revision="+revision+",recipients="+recipientIds.size()+",nonce=<redacted>]";}
}
