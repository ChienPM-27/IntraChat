package vn.intrachat.domain;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** One verified device in a signed group roster. Role cannot be inferred from UI nickname. */
public record GroupMember(UUID deviceId,byte[] spkiSha256,Role role) {
    public enum Role { OWNER, MEMBER }
    public GroupMember {
        GroupIdentity.requireId(deviceId,"member deviceId");
        Objects.requireNonNull(role,"role");
        if(spkiSha256==null || spkiSha256.length!=32)
            throw new IllegalArgumentException("Member SPKI fingerprint must be 32 bytes");
        spkiSha256=spkiSha256.clone();
    }
    @Override public byte[] spkiSha256(){return spkiSha256.clone();}
    @Override public boolean equals(Object obj){
        return this==obj || obj instanceof GroupMember m && deviceId.equals(m.deviceId)
                && role==m.role && Arrays.equals(spkiSha256,m.spkiSha256);
    }
    @Override public int hashCode(){return 31*Objects.hash(deviceId,role)+Arrays.hashCode(spkiSha256);}
    @Override public String toString(){return "GroupMember[deviceId="+deviceId+",role="+role+",spkiSha256=<redacted>]";}
}
