package vn.intrachat.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Profile-bound two-peer V3 transcript; never reuse the V2 SAS for Group capability. */
public final class GroupV3Transcript {
    private GroupV3Transcript(){}
    public static byte[] hash(UUID requestId,UUID a,byte[] fpA,byte[] nonceA,
                              UUID b,byte[] fpB,byte[] nonceB){
        requireId(requestId);requireId(a);requireId(b);
        if(a.equals(b))throw new IllegalArgumentException("Pair must have two different devices");
        byte[] one=tuple(a,fpA,nonceA),two=tuple(b,fpB,nonceB);
        if(Arrays.compareUnsigned(one,two)>0){byte[] tmp=one;one=two;two=tmp;}
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(buffer);
            byte[] domain="IntraChat-PAIR-V3-GROUP".getBytes(StandardCharsets.US_ASCII);
            out.writeInt(domain.length);out.write(domain);
            out.writeInt(one.length);out.write(one);out.writeInt(two.length);out.write(two);
            byte[] profile="CHAT_V3_GROUP".getBytes(StandardCharsets.US_ASCII);
            out.writeShort(profile.length);out.write(profile);
            out.writeByte(2);
            for(String cap:List.of("CHAT_TLS13_V2","GROUP_V1")){
                byte[] ascii=cap.getBytes(StandardCharsets.US_ASCII);
                out.writeByte(ascii.length);out.write(ascii);
            }
            writeUuid(out,requestId);
            return MessageDigest.getInstance("SHA-256").digest(buffer.toByteArray());
        }catch(IOException|NoSuchAlgorithmException impossible){
            throw new IllegalStateException("Cannot hash Group v3 pairing transcript",impossible);
        }
    }
    public static String sas(byte[] digest){
        if(digest==null||digest.length!=32)throw new IllegalArgumentException("Expected SHA256 digest");
        String hex=HexFormat.of().withUpperCase().formatHex(digest,0,8);
        return hex.substring(0,4)+"-"+hex.substring(4,8)+"-"+
            hex.substring(8,12)+"-"+hex.substring(12,16);
    }
    private static byte[] tuple(UUID id,byte[] fp,byte[] nonce){
        if(fp==null||fp.length!=32||nonce==null||nonce.length!=32)
            throw new IllegalArgumentException("Invalid verified SPKI/nonce length");
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream(80);
            DataOutputStream out=new DataOutputStream(buffer);
            writeUuid(out,id);out.write(fp);out.write(nonce);
            return buffer.toByteArray();
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
    }
    private static void writeUuid(DataOutputStream out,UUID id)throws IOException{
        out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
    }
    private static void requireId(UUID id){
        Objects.requireNonNull(id);
        if(id.equals(new UUID(0,0)))throw new IllegalArgumentException("Zero UUID not valid for identity");
    }
}
