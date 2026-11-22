package vn.intrachat.domain;

import vn.intrachat.protocol.ChatV2Message;
import vn.intrachat.protocol.ChatV2Type;
import java.time.Instant;
import java.util.*;

/**
 * View event schema: a READ receipt can only be sent by real foreground app
 * visibility logic. The date is remote-supplied metadata, not human gaze proof.
 */
public record ReadReceipt(UUID receiptMessageId,UUID originalMessageId,
                          UUID readEventId,UUID sessionId,UUID conversationId,
                          UUID verifiedSenderClaim,UUID destinationId,Instant viewedAtUTC) {
    public ReadReceipt {
        check(receiptMessageId);check(originalMessageId);check(readEventId);
        check(sessionId);check(conversationId);check(verifiedSenderClaim);check(destinationId);
        if(verifiedSenderClaim.equals(destinationId))
            throw new IllegalArgumentException("Read receipt endpoints must differ");
        Objects.requireNonNull(viewedAtUTC);
    }
    public static ReadReceipt from(ChatV2Message frame) {
        Objects.requireNonNull(frame);
        if(frame.type()!=ChatV2Type.READ_RECEIPT)
            throw new IllegalArgumentException("Not a READ_RECEIPT");
        Map<String,Object> values=frame.payload();
        if(values.size()!=3)throw new IllegalArgumentException("Wrong read receipt fields");
        return new ReadReceipt(frame.messageId(),id(values.get("originalMessageId")),
            id(values.get("readEventId")),frame.sessionId(),frame.conversationId(),
            frame.senderId(),frame.destinationId(),instant(values.get("viewedAtUTC")));
    }
    private static UUID id(Object value){
        try{
            if(!(value instanceof String s)||!s.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new IllegalArgumentException("Invalid read UUID");
            UUID id=UUID.fromString(s);
            check(id);
            return id;
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid read receipt UUID",invalid);}
    }
    private static Instant instant(Object value){
        try{
            if(!(value instanceof String text))throw new IllegalArgumentException();
            return Instant.parse(text);
        }catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid viewedAtUTC",invalid);}
    }
    private static void check(UUID id){
        Objects.requireNonNull(id);
        if(id.equals(new UUID(0,0)))throw new IllegalArgumentException("Zero read receipt UUID forbidden");
    }
}
