package vn.intrachat.domain;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Stable, nonsecret conversation identifier from exactly two verified device identities. */
public final class P2PConversation {
    private P2PConversation() { }
    public static UUID idFor(UUID a,byte[] pinnedA,UUID b,byte[] pinnedB) {
        if(a==null||b==null||a.equals(b))throw new IllegalArgumentException("Two distinct verified device IDs required");
        byte[] left=key(a,pinnedA),right=key(b,pinnedB);
        if(Arrays.compareUnsigned(left,right)>0){byte[] swap=left;left=right;right=swap;}
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream d=new DataOutputStream(bytes);
            d.write("IntraChat-CONVERSATION-V2".getBytes(StandardCharsets.US_ASCII));
            d.write(left);d.write(right);
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            hash[6]=(byte)((hash[6]&0x0f)|0x50);
            hash[8]=(byte)((hash[8]&0x3f)|0x80);
            java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(hash);
            return new UUID(buffer.getLong(),buffer.getLong());
        } catch(IOException|NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static byte[] key(UUID id,byte[] fingerprint) {
        if(fingerprint==null||fingerprint.length!=32)throw new IllegalArgumentException("SPKI fingerprint must be 32 bytes");
        java.nio.ByteBuffer b=java.nio.ByteBuffer.allocate(48);
        b.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).put(fingerprint);
        return b.array();
    }
}
