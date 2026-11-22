package vn.intrachat.security;

import vn.intrachat.domain.*;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.util.*;

/**
 * Owner roster/grant signatures. Does not itself grant READY or replace TLS pin validation.
 * Caller must additionally check actual verified TLS sender/recipient/session and current roster.
 */
public final class GroupOwnerSignature {
    private GroupOwnerSignature(){}
    public static byte[] signRoster(GroupRoster roster,PrivateKey privateKey,PublicKey ownerKey)
        throws GeneralSecurityException{
        verifyKeyBinding(roster.identity().ownerSpkiSha256(),ownerKey);
        return sign(GroupCanonicalBytes.roster(roster),privateKey,ownerKey);
    }
    public static byte[] signGrant(GroupGrant grant,PrivateKey privateKey,PublicKey ownerKey)
        throws GeneralSecurityException{
        verifyKeyBinding(grant.ownerSpkiSha256(),ownerKey);
        return sign(GroupCanonicalBytes.grant(grant),privateKey,ownerKey);
    }
    public static boolean verifyRoster(GroupRoster roster,byte[] signature,PublicKey pinnedOwnerKey){
        try{
            verifyKeyBinding(roster.identity().ownerSpkiSha256(),pinnedOwnerKey);
            return verify(GroupCanonicalBytes.roster(roster),signature,pinnedOwnerKey);
        }catch(Exception e){return false;}
    }
    public static boolean verifyGrant(GroupGrant grant,byte[] signature,PublicKey pinnedOwnerKey){
        try{
            verifyKeyBinding(grant.ownerSpkiSha256(),pinnedOwnerKey);
            return verify(GroupCanonicalBytes.grant(grant),signature,pinnedOwnerKey);
        }catch(Exception e){return false;}
    }
    /**
     * Check a new owner-signed, strictly sequential revision.
     * First committed revision 1 must use all-zero previous hash. Rollback snapshots
     * loaded from disk stay STALE and require online owner reauthorization.
     */
    public static boolean acceptCommit(GroupMetadata candidate,GroupMetadata current,PublicKey pinnedOwnerKey){
        if(candidate==null || !verifyRoster(candidate.roster(),candidate.ownerSignature(),pinnedOwnerKey))
            return false;
        GroupRoster next=candidate.roster();
        if(current==null){
            byte[] zero=new byte[32];
            return next.revision()==1 && MessageDigest.isEqual(next.previousRosterHash(),zero);
        }
        GroupRoster before=current.roster();
        if(next.revision()!=before.revision()+1 || before.revision()==Long.MAX_VALUE
            || !next.identity().groupId().equals(before.identity().groupId())
            || !next.identity().ownerDeviceId().equals(before.identity().ownerDeviceId())
            || !MessageDigest.isEqual(next.identity().ownerSpkiSha256(),before.identity().ownerSpkiSha256()))
            return false;
        return MessageDigest.isEqual(next.previousRosterHash(),GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(before)))
            && verifyRoster(before,current.ownerSignature(),pinnedOwnerKey);
    }
    private static byte[] sign(byte[] bytes,PrivateKey privateKey,PublicKey ownerKey)throws GeneralSecurityException{
        Objects.requireNonNull(privateKey,"privateKey");
        if(!"EC".equalsIgnoreCase(privateKey.getAlgorithm()))
            throw new InvalidKeyException("Owner signing requires EC P-256");
        Signature s=Signature.getInstance("SHA256withECDSA");
        s.initSign(privateKey);s.update(bytes);
        byte[] output=s.sign();
        if(!verify(bytes,output,ownerKey))
            throw new SignatureException("Signing key does not match verified owner public key");
        return output;
    }
    private static boolean verify(byte[] bytes,byte[] signature,PublicKey key)throws GeneralSecurityException{
        if(signature==null||signature.length<64||signature.length>128)return false;
        Signature s=Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);s.update(bytes);return s.verify(signature);
    }
    private static void verifyKeyBinding(byte[] expected,PublicKey key)throws GeneralSecurityException{
        Objects.requireNonNull(expected,"owner fingerprint");
        if(!(key instanceof ECPublicKey ec)||!"EC".equalsIgnoreCase(key.getAlgorithm())
            ||ec.getParams().getOrder().bitLength()!=256
            ||ec.getParams().getCurve().getField().getFieldSize()!=256)
            throw new InvalidKeyException("Owner public key must be EC P-256");
        if(!MessageDigest.isEqual(expected,FingerprintVerifier.spkiFingerprint(key)))
            throw new InvalidKeyException("Owner public key does not match pinned SPKI digest");
    }
}
