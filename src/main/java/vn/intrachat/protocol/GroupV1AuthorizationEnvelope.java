package vn.intrachat.protocol;

import vn.intrachat.domain.*;
import vn.intrachat.security.*;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;

/**
 * Explicit Group TEXT authorization over ALREADY verified TLS 1.3 with bilateral
 * GROUP_V1 capability. Neither this DTO nor JSON parsing establishes TLS trust;
 * the future session router MUST extract the Channel fields from the actual TLS
 * session and negotiation transcript (not from received frame data).
 *
 * Distinct typed Group ACK/READ and owner control frames need their own gates.
 * This API deliberately rejects them rather than interpreting them as content.
 */
public final class GroupV1AuthorizationEnvelope {
    private GroupV1AuthorizationEnvelope(){}
    public record Channel(UUID sessionId,UUID verifiedPeerId,UUID localDeviceId,
                          byte[] verifiedPeerSpkiSha256,boolean tlsReady,
                          boolean groupCapability) {
        public Channel {
            Objects.requireNonNull(sessionId);Objects.requireNonNull(verifiedPeerId);
            Objects.requireNonNull(localDeviceId);
            if(sessionId.equals(new UUID(0,0))||verifiedPeerId.equals(localDeviceId)
                ||verifiedPeerSpkiSha256==null||verifiedPeerSpkiSha256.length!=32)
                throw new IllegalArgumentException("Invalid Group TLS channel metadata");
            verifiedPeerSpkiSha256=verifiedPeerSpkiSha256.clone();
        }
        @Override public byte[] verifiedPeerSpkiSha256(){return verifiedPeerSpkiSha256.clone();}
    }

    /**
     * Validate only a GROUP_TEXT message, including exact recipient and owner
     * signed grant. This does NOT mutate an inbound dedup ledger or generate ACK.
     * The eventual receiver must additionally reject seen messageId/nonce and
     * use the latest fresh owner revision (not blindly load stale disk metadata).
     */
    public static GroupGrant authorize(GroupV1Message message,Channel channel,
        GroupMetadata committed,PublicKey trustedOwnerKey,Instant now)throws ProtocolException{
        try{
            Objects.requireNonNull(message);Objects.requireNonNull(channel);
            Objects.requireNonNull(committed);Objects.requireNonNull(trustedOwnerKey);
            Objects.requireNonNull(now);
            if(!channel.tlsReady()||!channel.groupCapability()
                ||message.type()!=GroupV1Type.GROUP_TEXT
                ||!message.sessionId().equals(channel.sessionId())
                ||!message.senderId().equals(channel.verifiedPeerId())
                ||!message.destinationId().equals(channel.localDeviceId()))
                throw new IllegalArgumentException("Group session/capability not authorized");
            // Never authorize a resurrected signed roster loaded from disk.
            if(committed.stale()||!GroupOwnerSignature.verifyRoster(committed.roster(),
                committed.ownerSignature(),trustedOwnerKey))
                throw new IllegalArgumentException("No fresh signed owner roster");
            GroupRoster roster=committed.roster();
            if(!roster.identity().groupId().equals(message.groupId())
                ||roster.revision()!=message.membershipRevision())
                throw new IllegalArgumentException("Wrong Group revision");
            GroupMember sender=null;
            boolean destinationMember=false;
            for(GroupMember m:roster.members()){
                if(m.deviceId().equals(message.senderId()))sender=m;
                if(m.deviceId().equals(message.destinationId()))destinationMember=true;
            }
            if(sender==null||!destinationMember
                ||!MessageDigest.isEqual(sender.spkiSha256(),channel.verifiedPeerSpkiSha256()))
                throw new IllegalArgumentException("Authenticated peer is not roster member");
            new GroupV1Codec().encode(message); // reject malformed text, unsafe/untyped fields
            byte[] canonical=Base64.getDecoder().decode(
                (String)message.payload().get("grantCanonicalBase64"));
            byte[] signature=Base64.getDecoder().decode(
                (String)message.payload().get("ownerSignatureBase64"));
            GroupGrant grant=GroupCanonicalBytes.parseGrant(canonical);
            if(!GroupOwnerSignature.verifyGrant(grant,signature,trustedOwnerKey)
                ||!grant.groupId().equals(message.groupId())
                ||!grant.messageId().equals(message.messageId())
                ||!grant.senderId().equals(message.senderId())
                ||grant.revision()!=message.membershipRevision()
                ||!grant.ownerDeviceId().equals(roster.identity().ownerDeviceId())
                ||!MessageDigest.isEqual(grant.ownerSpkiSha256(),roster.identity().ownerSpkiSha256())
                ||!MessageDigest.isEqual(grant.rosterHash(),
                    GroupCanonicalBytes.sha256(GroupCanonicalBytes.roster(roster)))
                ||!grant.recipientIds().contains(message.destinationId())
                ||now.isBefore(grant.issuedAt())||!now.isBefore(grant.expiresAt()))
                throw new IllegalArgumentException("Invalid signed Group authorization");
            // Exact recipient set is committed membership minus sender. No leaked
            // content to a guessed but not-authorized participant.
            Set<UUID> expected=new HashSet<>();
            for(GroupMember m:roster.members())
                if(!m.deviceId().equals(grant.senderId()))expected.add(m.deviceId());
            if(!expected.equals(new HashSet<>(grant.recipientIds())))
                throw new IllegalArgumentException("Grant recipient set differs from roster");
            return grant;
        }catch(Exception denied){
            // Do not echo text, group identifiers, fingerprints or signed bytes.
            throw new ProtocolException("GROUP_AUTHORIZATION_FAILED",
                "Group frame denied by authenticated capability, roster or signed ticket");
        }
    }
}
