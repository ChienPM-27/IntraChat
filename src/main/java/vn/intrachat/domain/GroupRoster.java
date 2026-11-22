package vn.intrachat.domain;

import java.time.Instant;
import java.util.*;

/**
 * Signed-data payload shape. This record DOES NOT itself authenticate a roster.
 * The owner signature, protected TLS peer and a fresh per-message owner ticket are required.
 */
public record GroupRoster(GroupIdentity identity,long revision,byte[] previousRosterHash,
                          List<GroupMember> members,UUID commitEventId,Instant committedAt) {
    public static final int MAX_MEMBERS=5;
    public GroupRoster {
        Objects.requireNonNull(identity,"identity");
        if(revision<1)throw new IllegalArgumentException("Uncommitted or overflowing revision");
        if(previousRosterHash==null||previousRosterHash.length!=32)
            throw new IllegalArgumentException("Previous roster hash must be 32 bytes");
        previousRosterHash=previousRosterHash.clone();
        GroupIdentity.requireId(commitEventId,"commitEventId");
        Objects.requireNonNull(committedAt,"committedAt");
        if(committedAt.isBefore(Instant.EPOCH))throw new IllegalArgumentException("Invalid UTC time");
        Objects.requireNonNull(members,"members");
        if(members.size()<2 || members.size()>MAX_MEMBERS)
            throw new IllegalArgumentException("Group must contain 2..5 members including owner");
        List<GroupMember> ordered=new ArrayList<>(members);
        ordered.sort(Comparator.comparing(GroupMember::deviceId,
            (a,b)->compareUuid(a,b)));
        Set<UUID> uniqueIds=new HashSet<>();
        Set<String> uniquePins=new HashSet<>();
        int owners=0;
        for(GroupMember m:ordered) {
            Objects.requireNonNull(m,"member");
            if(!uniqueIds.add(m.deviceId()))throw new IllegalArgumentException("Duplicate group member ID");
            if(!uniquePins.add(HexFormat.of().formatHex(m.spkiSha256())))
                throw new IllegalArgumentException("Duplicate group member public key");
            if(m.role()==GroupMember.Role.OWNER) {
                owners++;
                if(!m.deviceId().equals(identity.ownerDeviceId())
                    || !java.security.MessageDigest.isEqual(m.spkiSha256(),identity.ownerSpkiSha256()))
                    throw new IllegalArgumentException("Owner role does not match verified owner");
            } else if(m.deviceId().equals(identity.ownerDeviceId()))
                throw new IllegalArgumentException("Owner must have OWNER role");
        }
        if(owners!=1)throw new IllegalArgumentException("Exactly one owner is required");
        members=List.copyOf(ordered);
    }
    private static int compareUuid(UUID a,UUID b){
        int hi=Long.compareUnsigned(a.getMostSignificantBits(),b.getMostSignificantBits());
        return hi!=0?hi:Long.compareUnsigned(a.getLeastSignificantBits(),b.getLeastSignificantBits());
    }
    @Override public byte[] previousRosterHash(){return previousRosterHash.clone();}
    @Override public boolean equals(Object obj) {
        return this==obj || obj instanceof GroupRoster r && identity.equals(r.identity)
            && revision==r.revision && Arrays.equals(previousRosterHash,r.previousRosterHash)
            && members.equals(r.members) && commitEventId.equals(r.commitEventId)
            && committedAt.equals(r.committedAt);
    }
    @Override public int hashCode(){return Objects.hash(identity,revision,Arrays.hashCode(previousRosterHash),members,commitEventId,committedAt);}
    @Override public String toString(){return "GroupRoster[groupId="+identity.groupId()+",revision="+revision+",members="+members.size()+",signature=not included]";}
}
