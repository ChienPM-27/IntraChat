package vn.intrachat.application;

import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;

/**
 * Prototype signed Group inbound authorization and session-only RAM mailbox.
 * Future v3 transport reader MUST obtain the channel from actual TLS/pinned
 * identity and BOTH negotiated GROUP_V1 capabilities before calling accept.
 *
 * No group user payload is enabled on the existing v1/v2 socket dispatcher.
 * Owner cannot relay text; each message is addressed to one verified peer.
 */
public final class GroupInboundMailbox implements AutoCloseable {
    public static final int MAX_ACTIVE_GROUPS=8;
    public static final int MAX_MESSAGES=500;
    public static final int MAX_BYTES_PER_GROUP=2*1024*1024;
    public static final int MAX_BYTES_GLOBAL=8*1024*1024;
    public static final int MAX_DEDUP=1024;

    public record Message(UUID groupId,UUID messageId,UUID verifiedSenderId,
                          UUID recipientId,long revision,Instant acceptedAt,String text){}
    public record Accepted(Message message,GroupV1Message ack){}

    private static final class Bucket {
        final Map<UUID,Message> rows=new LinkedHashMap<>();
        final Set<String> usedNonces=new HashSet<>();
        int bytes;
    }
    private final Map<UUID,Bucket> groups=new HashMap<>();
    private int totalBytes;
    private boolean closed;
    public synchronized int acceptedCount(){
        return groups.values().stream().mapToInt(b->b.rows.size()).sum();
    }
    public synchronized int activeGroups(){return groups.size();}
    public synchronized List<Message> transcript(UUID groupId){
        Bucket b=groups.get(groupId);
        return b==null?List.of():List.copyOf(b.rows.values());
    }

    /**
     * Verification and all memory reservations happen before any ACK exists.
     * Throws a non-sensitive error on invalid owner signature, peer session,
     * stale roster, expired/replayed grant, or RAM pressure.
     */
    public synchronized Accepted accept(GroupV1Message text,
        GroupV1AuthorizationEnvelope.Channel channel,GroupMetadata committed,
        PublicKey pinnedOwnerKey,Instant now)throws ProtocolException{
        if(closed)throw new ProtocolException("GROUP_NOT_AVAILABLE","Group mailbox closed");
        // Signature/TLS/capability/recipient/revision checks precede any mutation.
        GroupGrant grant=GroupV1AuthorizationEnvelope.authorize(text,channel,
            committed,pinnedOwnerKey,now);
        Bucket target=groups.get(text.groupId());
        if(target==null&&groups.size()>=MAX_ACTIVE_GROUPS)
            throw new ProtocolException("GROUP_CAPACITY","Group active mailbox count exhausted");
        int bytes=((String)text.payload().get("text")).getBytes(StandardCharsets.UTF_8).length;
        if(target!=null
            &&(target.rows.containsKey(text.messageId())
                ||target.usedNonces.contains(HexFormat.of().formatHex(grant.nonce()))))
            throw new ProtocolException("GROUP_REPLAY_DENIED","Repeated Group message or owner nonce");
        int currentCount=target==null?0:target.rows.size();
        int currentSize=target==null?0:target.bytes;
        if(currentCount>=MAX_MESSAGES||currentCount>=MAX_DEDUP
            ||currentSize>MAX_BYTES_PER_GROUP-bytes
            ||totalBytes>MAX_BYTES_GLOBAL-bytes)
            throw new ProtocolException("GROUP_CAPACITY","Group RAM message quota exceeded");

        // This is a local, authenticated app acceptance, not a TCP write and
        // not evidence of a real human reading the received text.
        Message content=new Message(text.groupId(),text.messageId(),channel.verifiedPeerId(),
            channel.localDeviceId(),text.membershipRevision(),now,
            (String)text.payload().get("text"));
        GroupV1Message receipt=new GroupV1Message(UUID.randomUUID(),
            GroupV1Type.GROUP_DELIVERY_ACK,channel.sessionId(),
            channel.localDeviceId(),channel.verifiedPeerId(),text.groupId(),
            text.membershipRevision(),now,
            Map.of("originalMessageId",text.messageId().toString(),
                "acceptedAtUTC",now.toString(),"result","ACCEPTED"));
        // Construct and strictly validate the ACK before inserting the text:
        // failure must not create a bubble without a retrievable receipt.
        new GroupV1Codec().encode(receipt);
        if(target==null){target=new Bucket();groups.put(text.groupId(),target);}
        target.rows.put(text.messageId(),content);
        target.usedNonces.add(HexFormat.of().formatHex(grant.nonce()));
        target.bytes+=bytes;
        totalBytes+=bytes;
        return new Accepted(content,receipt);
    }
    public synchronized void eraseGroup(UUID groupId){
        Bucket removed=groups.remove(groupId);
        if(removed!=null){
            totalBytes-=removed.bytes;
            removed.rows.clear();
            removed.usedNonces.clear();
            removed.bytes=0;
        }
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;
        for(UUID id:List.copyOf(groups.keySet()))eraseGroup(id);
        totalBytes=0;
    }
}
