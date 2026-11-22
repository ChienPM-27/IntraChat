package vn.intrachat.domain;

import vn.intrachat.protocol.ChatV2Message;
import vn.intrachat.protocol.ChatV2Type;
import java.time.Instant;
import java.util.*;

/**
 * Application DELIVERED proof schema. Parsing this DTO does NOT authenticate
 * the claimed sender; transport must independently match TLS identity, session,
 * conversation and original outgoing attempt before elevating local state.
 */
public record DeliveryReceipt(UUID receiptMessageId,UUID originalMessageId,
                              UUID sessionId,UUID conversationId,
                              UUID verifiedSenderClaim,UUID destinationId,
                              Instant acceptedAtUTC) {
    public DeliveryReceipt {
        validate(receiptMessageId);validate(originalMessageId);
        validate(sessionId);validate(conversationId);
        validate(verifiedSenderClaim);validate(destinationId);
        if(verifiedSenderClaim.equals(destinationId))
            throw new IllegalArgumentException("Receipt endpoints must differ");
        Objects.requireNonNull(acceptedAtUTC);
    }
    public static DeliveryReceipt from(ChatV2Message frame) {
        Objects.requireNonNull(frame);
        if(frame.type()!=ChatV2Type.DELIVERY_ACK)
            throw new IllegalArgumentException("Not a DELIVERY_ACK");
        Map<String,Object> values=frame.payload();
        if(values.size()!=3||!"ACCEPTED".equals(values.get("result")))
            throw new IllegalArgumentException("Invalid accepted-into-RAM result");
        return new DeliveryReceipt(frame.messageId(),id(values.get("originalMessageId")),
            frame.sessionId(),frame.conversationId(),frame.senderId(),
            frame.destinationId(),instant(values.get("acceptedAtUTC")));
    }
    private static UUID id(Object value){
        try{
            if(!(value instanceof String s)||!s.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new IllegalArgumentException("Invalid receipt UUID");
            UUID parsed=UUID.fromString(s);
            validate(parsed);
            return parsed;
        }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid receipt UUID",bad);}
    }
    private static Instant instant(Object value){
        try{
            if(!(value instanceof String text))throw new IllegalArgumentException();
            return Instant.parse(text);
        }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid receipt timestamp",bad);}
    }
    private static void validate(UUID id){
        Objects.requireNonNull(id);
        if(id.equals(new UUID(0,0)))throw new IllegalArgumentException("Zero receipt UUID forbidden");
    }
}
