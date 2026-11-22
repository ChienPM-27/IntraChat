package vn.intrachat.domain;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identity for a real, owner-controlled Live group.
 * A group title, remote IP or claimed UUID is never proof of group authorization.
 */
public record GroupIdentity(UUID groupId, String title, UUID ownerDeviceId, byte[] ownerSpkiSha256) {
    public GroupIdentity {
        requireId(groupId, "groupId");
        requireId(ownerDeviceId, "ownerDeviceId");
        if (groupId.equals(ownerDeviceId)) throw new IllegalArgumentException("Group and owner IDs must differ");
        Objects.requireNonNull(title, "title");
        // Reject controls in the original input too: strip() must not silently erase them.
        if (title.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Control character in group title");
        title = Normalizer.normalize(title.strip(), Normalizer.Form.NFC);
        if (title.isBlank() || title.codePointCount(0,title.length()) > 60
                || title.getBytes(StandardCharsets.UTF_8).length > 240)
            throw new IllegalArgumentException("Invalid group title length");
        for (int i=0;i<title.length();) {
            char c=title.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i+1>=title.length() || !Character.isLowSurrogate(title.charAt(i+1)))
                    throw new IllegalArgumentException("Malformed group title Unicode");
                i+=2;
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Malformed group title Unicode");
            } else {
                if (Character.isISOControl(c)) throw new IllegalArgumentException("Control character in group title");
                i++;
            }
        }
        if (ownerSpkiSha256 == null || ownerSpkiSha256.length != 32)
            throw new IllegalArgumentException("Owner SPKI SHA256 must be 32 bytes");
        ownerSpkiSha256 = ownerSpkiSha256.clone();
    }

    public static void requireId(UUID id,String field) {
        Objects.requireNonNull(id,field);
        if (id.equals(new UUID(0,0))) throw new IllegalArgumentException(field+" cannot be zero UUID");
    }
    @Override public byte[] ownerSpkiSha256() {return ownerSpkiSha256.clone();}
    @Override public boolean equals(Object obj) {
        return this==obj || obj instanceof GroupIdentity other && groupId.equals(other.groupId)
                && title.equals(other.title) && ownerDeviceId.equals(other.ownerDeviceId)
                && java.util.Arrays.equals(ownerSpkiSha256,other.ownerSpkiSha256);
    }
    @Override public int hashCode() {
        return 31*Objects.hash(groupId,title,ownerDeviceId)+java.util.Arrays.hashCode(ownerSpkiSha256);
    }
    @Override public String toString() {
        return "GroupIdentity[groupId="+groupId+",title="+title+",ownerDeviceId="+ownerDeviceId+",ownerSpkiSha256=<redacted>]";
    }
}
