package vn.intrachat.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Canonical binary two-device SAS; never uses display names, network addresses or remote-provided SAS. */
public final class FingerprintVerifier {
    private FingerprintVerifier() { }
    public static byte[] spkiFingerprint(PublicKey publicKey) {
        Objects.requireNonNull(publicKey);
        return sha256(publicKey.getEncoded());
    }
    public static boolean sameFingerprint(byte[] expected,byte[] candidate) {
        return expected!=null&&candidate!=null&&expected.length==32&&candidate.length==32
               &&MessageDigest.isEqual(expected,candidate);
    }
    public static String fullFingerprint(byte[] hash) {
        require32(hash,"fingerprint");
        return HexFormat.of().withUpperCase().formatHex(hash);
    }
    public static String sharedCode(UUID a,byte[] fingerprintA,byte[] nonceA,
                                    UUID b,byte[] fingerprintB,byte[] nonceB) {
        byte[] hash=transcriptHash(a,fingerprintA,nonceA,b,fingerprintB,nonceB);
        String hex=HexFormat.of().withUpperCase().formatHex(hash,0,8);
        return hex.substring(0,4)+"-"+hex.substring(4,8)+"-"+
               hex.substring(8,12)+"-"+hex.substring(12,16);
    }
    /** Full transcript digest also binds both protected pairing control messages. */
    public static byte[] transcriptHash(UUID a,byte[] fingerprintA,byte[] nonceA,
                                       UUID b,byte[] fingerprintB,byte[] nonceB) {
        byte[] left=tuple(a,fingerprintA,nonceA);
        byte[] right=tuple(b,fingerprintB,nonceB);
        if(a.equals(b))throw new IllegalArgumentException("Pair must contain different device IDs");
        if(Arrays.compareUnsigned(left,right)>0){byte[] tmp=left;left=right;right=tmp;}
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream d=new DataOutputStream(bytes);
            byte[] domain="IntraChat-PAIR-V2".getBytes(StandardCharsets.US_ASCII);
            d.writeInt(domain.length);d.write(domain);
            d.writeInt(left.length);d.write(left);
            d.writeInt(right.length);d.write(right);
            return sha256(bytes.toByteArray());
        } catch(IOException impossible) {throw new IllegalStateException(impossible);}
    }
    private static byte[] tuple(UUID id,byte[] fp,byte[] nonce){
        Objects.requireNonNull(id);
        require32(fp,"fingerprint");
        require32(nonce,"nonce");
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream d=new DataOutputStream(bytes);
            d.writeLong(id.getMostSignificantBits());d.writeLong(id.getLeastSignificantBits());
            d.write(fp);d.write(nonce);
            return bytes.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private static void require32(byte[] bytes,String kind){
        if(bytes==null||bytes.length!=32)throw new IllegalArgumentException(kind+" must be 32 bytes");
    }
    private static byte[] sha256(byte[] bytes){
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
