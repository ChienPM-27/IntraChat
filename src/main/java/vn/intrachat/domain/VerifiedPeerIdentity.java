package vn.intrachat.domain;

import java.security.MessageDigest;
import java.util.*;

/**
 * Immutable evidence of a verified device and this exact pairing transcript.
 * This value MUST only be constructed after mutual TLS, bilateral protected
 * CONFIRM/READY, and durable pin matching. It is not a substitute for TLS.
 */
public record VerifiedPeerIdentity(UUID deviceId,byte[] spkiSha256,UUID requestId,
                                   byte[] transcriptSha256,UUID conversationId) {
    public VerifiedPeerIdentity {
        Objects.requireNonNull(deviceId,"deviceId");
        Objects.requireNonNull(requestId,"requestId");
        Objects.requireNonNull(conversationId,"conversationId");
        if(deviceId.equals(new UUID(0,0))||requestId.equals(new UUID(0,0))
            ||conversationId.equals(new UUID(0,0)))
            throw new IllegalArgumentException("Zero verification identity forbidden");
        if(spkiSha256==null||spkiSha256.length!=32
            ||transcriptSha256==null||transcriptSha256.length!=32)
            throw new IllegalArgumentException("Fingerprint/transcript must be SHA-256");
        spkiSha256=spkiSha256.clone();
        transcriptSha256=transcriptSha256.clone();
    }
    @Override public byte[] spkiSha256(){return spkiSha256.clone();}
    @Override public byte[] transcriptSha256(){return transcriptSha256.clone();}
    @Override public boolean equals(Object other){
        if(this==other)return true;
        if(!(other instanceof VerifiedPeerIdentity v))return false;
        return deviceId.equals(v.deviceId)&&requestId.equals(v.requestId)
            &&conversationId.equals(v.conversationId)
            &&MessageDigest.isEqual(spkiSha256,v.spkiSha256)
            &&MessageDigest.isEqual(transcriptSha256,v.transcriptSha256);
    }
    @Override public int hashCode(){
        return Objects.hash(deviceId,requestId,conversationId,Arrays.hashCode(spkiSha256),
            Arrays.hashCode(transcriptSha256));
    }
    @Override public String toString(){
        return "VerifiedPeerIdentity[deviceId="+deviceId+", requestId="+requestId+
            ", conversationId="+conversationId+", fingerprints=REDACTED]";
    }
}
