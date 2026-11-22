package vn.intrachat.protocol;

import java.time.Instant;
import java.util.*;

/** Untrusted decoded GROUP_V1 data: MUST still be checked against verified TLS and owner authorization. */
public record GroupV1Message(UUID messageId, GroupV1Type type, UUID sessionId, UUID senderId,
        UUID destinationId, UUID groupId, long membershipRevision, Instant sentAt,
        Map<String,Object> payload) {
    public GroupV1Message {
        Objects.requireNonNull(messageId); Objects.requireNonNull(type);
        Objects.requireNonNull(sessionId); Objects.requireNonNull(senderId);
        Objects.requireNonNull(destinationId); Objects.requireNonNull(groupId);
        Objects.requireNonNull(sentAt); Objects.requireNonNull(payload);
        Map<String,Object> copy=new LinkedHashMap<>();
        for(var e:payload.entrySet()){
            Objects.requireNonNull(e.getKey(),"payload key");
            Object value=e.getValue();
            if(value instanceof String s)copy.put(e.getKey(),s);
            else if(value instanceof List<?> l)copy.put(e.getKey(),List.copyOf(l));
            else throw new IllegalArgumentException("Only textual or bounded list group payloads allowed");
        }
        payload=Collections.unmodifiableMap(copy);
    }
}
