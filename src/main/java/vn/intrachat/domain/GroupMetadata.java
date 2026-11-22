package vn.intrachat.domain;

import java.util.Arrays;
import java.util.Objects;

/**
 * In-memory representation of persistable signed group metadata.
 * Loading metadata is always STALE until verified owner reconfirms it.
 */
public record GroupMetadata(GroupRoster roster,byte[] ownerSignature,boolean stale) {
    public GroupMetadata {
        Objects.requireNonNull(roster,"roster");
        if(ownerSignature==null||ownerSignature.length<64||ownerSignature.length>128)
            throw new IllegalArgumentException("Owner signature must be bounded ECDSA DER");
        ownerSignature=ownerSignature.clone();
    }
    @Override public byte[] ownerSignature(){return ownerSignature.clone();}
    @Override public boolean equals(Object obj){return this==obj || obj instanceof GroupMetadata g
        && roster.equals(g.roster) && stale==g.stale && Arrays.equals(ownerSignature,g.ownerSignature);}
    @Override public int hashCode(){return Objects.hash(roster,stale,Arrays.hashCode(ownerSignature));}
    @Override public String toString(){return "GroupMetadata[roster="+roster+",stale="+stale+",signature=<redacted>]";}
}
