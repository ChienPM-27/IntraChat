package vn.intrachat.protocol;
import java.time.Instant;
import java.util.*;
public record WireMessage(Type type,UUID messageId,UUID conversationId,
    UUID senderId,UUID recipientId,Instant sentAtUtc,Map<String,String> payload){
    public enum Type {HELLO,WELCOME,REJECT,TEXT,ACK,READ,ERROR,
        GROUP_INVITE,GROUP_ACCEPT,GROUP_REJECT,GROUP_UPDATE,GROUP_REMOVE,GROUP_ACK,GROUP_LEAVE,GROUP_TEXT,GROUP_MEDIA_STATUS,GROUP_SYNC,
        FILE_OFFER,FILE_ACCEPT,FILE_REJECT,FILE_CHUNK,FILE_DONE,FILE_RESULT,FILE_CANCEL,FILE_ERROR}
    public WireMessage {
        if(type==null||senderId==null)throw new IllegalArgumentException("Missing type/senderId");
        payload=payload==null?Map.of():Map.copyOf(payload);
        if(payload.size()>24||payload.entrySet().stream().anyMatch(e->e.getKey().length()>64))
            throw new IllegalArgumentException("Invalid payload");
        if(type==Type.HELLO||type==Type.WELCOME||type==Type.REJECT){
            if(type!=Type.REJECT&&!payload.containsKey("displayName"))
                throw new IllegalArgumentException("Missing displayName");
        } else if(messageId==null||conversationId==null||recipientId==null||sentAtUtc==null)
            throw new IllegalArgumentException("Missing envelope metadata");
        if(type==Type.TEXT&&(!payload.containsKey("text")||payload.get("text").isBlank()))
            throw new IllegalArgumentException("Missing text");
        if((type==Type.ACK||type==Type.READ)&&!payload.containsKey("originalMessageId"))
            throw new IllegalArgumentException("Missing receipt target");
    }
}
