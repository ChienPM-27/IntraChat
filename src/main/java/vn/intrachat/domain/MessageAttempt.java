package vn.intrachat.domain;

import java.util.Objects;
import java.util.UUID;

/** Message send state with monotonic evidence rules. Caller MUST authenticate receipts. */
public final class MessageAttempt {
    public enum Status { DRAFT, SENDING, LOCAL_SENT, APP_ACKNOWLEDGED, READ, FAILED, UNCERTAIN }
    private final UUID messageId;
    private Status state=Status.DRAFT;
    public MessageAttempt(UUID messageId){this.messageId=Objects.requireNonNull(messageId);}
    public UUID messageId(){return messageId;}
    public synchronized Status status(){return state;}
    public synchronized void startSending(){
        if(state!=Status.DRAFT)throw new IllegalStateException("Cannot start from "+state);
        state=Status.SENDING;
    }
    public synchronized void localWriteCompleted(){
        if(state==Status.APP_ACKNOWLEDGED||state==Status.READ)return; // authenticated early ACK
        if(state!=Status.SENDING)throw new IllegalStateException("Not sending");
        state=Status.LOCAL_SENT;
    }
    public synchronized void markUncertain(){
        if(state==Status.SENDING||state==Status.LOCAL_SENT)state=Status.UNCERTAIN;
        else if(state!=Status.UNCERTAIN)throw new IllegalStateException("Cannot mark uncertain from "+state);
    }
    public synchronized void failBeforeSend(){
        if(state==Status.DRAFT||state==Status.SENDING)state=Status.FAILED;
        else throw new IllegalStateException("Write outcome not conclusively failed");
    }
    public synchronized void onDeliveryAck(){
        if(state==Status.READ||state==Status.APP_ACKNOWLEDGED)return;
        if(state==Status.LOCAL_SENT||state==Status.SENDING||state==Status.UNCERTAIN)
            state=Status.APP_ACKNOWLEDGED;
        else throw new IllegalStateException("Unsolicited app ACK");
    }
    public synchronized void onViewReceipt(){
        if(state==Status.READ)return;
        if(state==Status.LOCAL_SENT||state==Status.APP_ACKNOWLEDGED||state==Status.UNCERTAIN)
            state=Status.READ;
        else throw new IllegalStateException("Unsolicited read receipt");
    }
}
