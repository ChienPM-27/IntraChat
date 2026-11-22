package vn.intrachat.application;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * View-only identity/time projection of an already authenticated M2 message.
 * Mutable display aliases and local time zones NEVER replace the verified
 * device UUID in routing or trust. Remote timestamps are informational only.
 */
public final class ChatViewMapper {
    private ChatViewMapper(){}

    public record Presented(UUID messageId,UUID conversationId,UUID verifiedSenderId,
                            UUID verifiedRemoteId,Instant utcInstant,
                            LocalDateTime localDateTime,String localizedTime,
                            String displayAlias,String text,boolean outgoing) {
        public Presented {
            Objects.requireNonNull(messageId);
            Objects.requireNonNull(conversationId);
            Objects.requireNonNull(verifiedSenderId);
            Objects.requireNonNull(verifiedRemoteId);
            Objects.requireNonNull(utcInstant);
            Objects.requireNonNull(localDateTime);
            Objects.requireNonNull(localizedTime);
            Objects.requireNonNull(displayAlias);
            Objects.requireNonNull(text);
        }
        public boolean clockSkewWarning(Instant reference,Duration allowedSkew) {
            Objects.requireNonNull(reference);
            Objects.requireNonNull(allowedSkew);
            if(allowedSkew.isNegative())throw new IllegalArgumentException("Negative clock-skew limit");
            return Duration.between(reference,utcInstant).abs().compareTo(allowedSkew)>0;
        }
    }

    public static Presented present(LiveChatService.Message message,UUID verifiedLocalId,
                                    UUID verifiedRemoteId,String alias,
                                    ZoneId zone,Locale locale){
        Objects.requireNonNull(message,"message");
        Objects.requireNonNull(verifiedLocalId,"verifiedLocalId");
        Objects.requireNonNull(verifiedRemoteId,"verifiedRemoteId");
        Objects.requireNonNull(alias,"alias");
        Objects.requireNonNull(zone,"zone");
        Objects.requireNonNull(locale,"locale");
        if(verifiedLocalId.equals(verifiedRemoteId)
            ||!Set.of(verifiedLocalId,verifiedRemoteId).contains(message.senderId())
            ||!Set.of(verifiedLocalId,verifiedRemoteId).contains(message.destinationId())
            ||message.senderId().equals(message.destinationId())
            ||message.outgoing()!=message.senderId().equals(verifiedLocalId))
            throw new IllegalArgumentException("Message endpoint is not bound to verified local/remote IDs");
        LocalDateTime shown=LocalDateTime.ofInstant(message.sentAt(),zone);
        String time=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(message.sentAt());
        return new Presented(message.messageId(),message.conversationId(),message.senderId(),
            verifiedRemoteId,message.sentAt(),shown,time,alias,
            message.text(),message.senderId().equals(verifiedLocalId));
    }
}
