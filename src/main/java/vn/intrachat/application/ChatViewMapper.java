package vn.intrachat.application;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * View-only identity/time projection of a received LAN message.
 * Display aliases do not affect UUID routing. Remote timestamps are informational only.
 */
public final class ChatViewMapper {
    private ChatViewMapper(){}

    public record Presented(UUID messageId,UUID conversationId,UUID senderId,
                            UUID remoteId,Instant utcInstant,
                            LocalDateTime localDateTime,String localizedTime,
                            String displayAlias,String text,boolean outgoing) {
        public Presented {
            Objects.requireNonNull(messageId);
            Objects.requireNonNull(conversationId);
            Objects.requireNonNull(senderId);
            Objects.requireNonNull(remoteId);
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

    public static Presented present(LiveChatService.Message message,UUID localId,
                                    UUID remoteId,String alias,
                                    ZoneId zone,Locale locale){
        Objects.requireNonNull(message,"message");
        Objects.requireNonNull(localId,"localId");
        Objects.requireNonNull(remoteId,"remoteId");
        Objects.requireNonNull(alias,"alias");
        Objects.requireNonNull(zone,"zone");
        Objects.requireNonNull(locale,"locale");
        if(localId.equals(remoteId)
            ||!Set.of(localId,remoteId).contains(message.senderId())
            ||!Set.of(localId,remoteId).contains(message.destinationId())
            ||message.senderId().equals(message.destinationId())
            ||message.outgoing()!=message.senderId().equals(localId))
            throw new IllegalArgumentException("Message endpoints do not match local/remote IDs");
        LocalDateTime shown=LocalDateTime.ofInstant(message.sentAt(),zone);
        String time=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(message.sentAt());
        return new Presented(message.messageId(),message.conversationId(),message.senderId(),
            remoteId,message.sentAt(),shown,time,alias,
            message.text(),message.senderId().equals(localId));
    }
}
