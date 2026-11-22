package vn.intrachat.application;

import vn.intrachat.protocol.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Bounded group OUTGOING recipient attempt ledger. Application DELIVERED is
 * accepted only from the exact verified TLS recipient + its current session.
 * READ is only a remote recipient's authenticated foreground-view event, never
 * inferred from TLS writes, ACK or a global group state.
 *
 * This component carries no text, drafts, grants or persistent storage.
 * It cannot generate a delivery/READ receipt itself.
 */
public final class GroupRecipientLedger implements AutoCloseable {
    public enum Status { SENDING, LOCAL_SENT, UNCERTAIN, FAILED, APP_ACKNOWLEDGED, READ }
    public static final int MAX_PENDING=128;
    private static final class Recipient {
        final UUID sessionId;
        Status status=Status.SENDING;
        Recipient(UUID sessionId){this.sessionId=sessionId;}
    }
    private static final class Outbound {
        final UUID groupId,senderId;
        final long revision;
        final Map<UUID,Recipient> recipients=new LinkedHashMap<>();
        Outbound(UUID groupId,long revision,UUID senderId){
            this.groupId=groupId;this.revision=revision;this.senderId=senderId;
        }
    }
    private final Map<UUID,Outbound> outbound=new HashMap<>();
    private int pending;
    private boolean closed;

    private static void requireId(UUID id){
        if(id==null||id.equals(new UUID(0,0)))
            throw new IllegalArgumentException("Nonzero Group UUID required");
    }
    private void requireOpen(){
        if(closed)throw new IllegalStateException("Group receipt ledger is closed");
    }
    public synchronized void register(UUID originalId,UUID groupId,long revision,
        UUID localSender,Map<UUID,UUID> recipientSessions)throws IOException{
        requireOpen();
        requireId(originalId);requireId(groupId);requireId(localSender);
        Objects.requireNonNull(recipientSessions);
        if(revision<1||recipientSessions.isEmpty()||recipientSessions.size()>4)
            throw new IllegalArgumentException("Invalid committed Group recipient count/revision");
        if(outbound.containsKey(originalId))throw new IOException("Duplicate Group outbound ID");
        if(recipientSessions.size()>MAX_PENDING-pending)
            throw new IOException("Group pending recipient receipt capacity exceeded");
        Outbound entry=new Outbound(groupId,revision,localSender);
        for(var recipient:recipientSessions.entrySet()){
            UUID id=recipient.getKey();
            requireId(id);requireId(recipient.getValue());
            if(id.equals(localSender))throw new IllegalArgumentException("Self Group recipient forbidden");
            entry.recipients.put(id,new Recipient(recipient.getValue()));
        }
        outbound.put(originalId,entry);
        pending+=entry.recipients.size();
    }
    public synchronized int pendingCount(){return pending;}
    public synchronized Status status(UUID messageId,UUID recipientId){
        Outbound entry=outbound.get(messageId);
        Recipient recipient=entry==null?null:entry.recipients.get(recipientId);
        return recipient==null?null:recipient.status;
    }
    public synchronized void localWriteCompleted(UUID messageId,UUID recipientId){
        requireOpen();
        Recipient recipient=get(messageId,recipientId);
        if(recipient.status==Status.SENDING)recipient.status=Status.LOCAL_SENT;
        // A TLS write cannot by itself publish DELIVERED or READ.
    }
    public synchronized void markUncertain(UUID messageId,UUID recipientId){
        requireOpen();
        Recipient recipient=get(messageId,recipientId);
        if(recipient.status==Status.SENDING||recipient.status==Status.LOCAL_SENT)
            recipient.status=Status.UNCERTAIN;
        // Never schedule a resend or synthesize a recipient ACK.
    }
    /**
     * Transport MUST obtain actualRemoteId/actualSessionId/localDeviceId from
     * its VERIFIED READY TLS peer, NOT claimed JSON fields.
     */
    public synchronized boolean onVerifiedReceipt(GroupV1Message receipt,UUID actualRemoteId,
        UUID actualSessionId,UUID localDeviceId)throws ProtocolException{
        requireOpen();
        try{
            Objects.requireNonNull(receipt);
            if(receipt.type()!=GroupV1Type.GROUP_DELIVERY_ACK
                &&receipt.type()!=GroupV1Type.GROUP_READ_RECEIPT)
                throw new IllegalArgumentException("Not Group recipient receipt");
            // Bounded strict schema and recognized ACCEPTED vs VIEWED fields.
            new GroupV1Codec().encode(receipt);
            requireId(actualRemoteId);requireId(actualSessionId);requireId(localDeviceId);
            UUID original=UUID.fromString((String)receipt.payload().get("originalMessageId"));
            Outbound sent=outbound.get(original);
            if(sent==null||!receipt.groupId().equals(sent.groupId)
                ||receipt.membershipRevision()!=sent.revision
                ||!receipt.destinationId().equals(sent.senderId)
                ||!localDeviceId.equals(sent.senderId)
                ||!receipt.senderId().equals(actualRemoteId)
                ||!receipt.sessionId().equals(actualSessionId))
                throw new IllegalArgumentException("Group receipt context mismatch");
            Recipient specific=sent.recipients.get(actualRemoteId);
            if(specific==null||!specific.sessionId.equals(actualSessionId))
                throw new IllegalArgumentException("Unknown or replaced Group recipient session");
            if(specific.status==Status.READ)return false;
            Status before=specific.status;
            if(receipt.type()==GroupV1Type.GROUP_DELIVERY_ACK){
                if(specific.status==Status.APP_ACKNOWLEDGED)return false;
                specific.status=Status.APP_ACKNOWLEDGED;
            }else{
                specific.status=Status.READ;
            }
            // A later READ improves B's state but must never free C's pending
            // capacity. A recipient slot is released exactly once.
            if(before!=Status.APP_ACKNOWLEDGED&&before!=Status.READ)pending--;
            return true;
        }catch(Exception invalid){
            throw new ProtocolException("GROUP_RECEIPT_INVALID",
                "Unverified or mismatched Group recipient acknowledgement");
        }
    }
    private Recipient get(UUID original,UUID peer){
        Outbound sent=outbound.get(original);
        if(sent==null||!sent.recipients.containsKey(peer))
            throw new IllegalArgumentException("Unknown Group attempt or recipient");
        return sent.recipients.get(peer);
    }
    public synchronized void cancel(UUID original){
        requireOpen();
        Outbound sent=outbound.remove(original);
        if(sent==null)return;
        for(Recipient recipient:sent.recipients.values())
            if(recipient.status!=Status.APP_ACKNOWLEDGED&&recipient.status!=Status.READ)
                pending--;
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;
        outbound.clear();pending=0;
    }
}
