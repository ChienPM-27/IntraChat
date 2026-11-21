package vn.intrachat.domain.history;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable source-envelope value for future opted-in real text/emoji History.
 * A non-empty originSeal here is only untrusted syntax; signature validation is a
 * separate security boundary and MUST occur before any durable import.
 */
public final class HistoryEvent {
    private static final UUID ZERO = new UUID(0L,0L);
    private final HistoryScope scope;
    private final UUID eventId, originalMessageId, originDeviceId;
    private final Instant sourceSentAtUtc;
    private final HistoryContent content;
    private final byte[] signerSpkiSha256, contentSha256, originSeal;
    private final int sourceProtocolVersion;

    private HistoryEvent(HistoryScope scope, UUID eventId, UUID originalMessageId,
                         UUID originDeviceId, Instant sourceSentAtUtc, HistoryContent content,
                         byte[] signerSpkiSha256, byte[] originSeal, int sourceProtocolVersion) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.eventId = nonzero(eventId,"eventId");
        this.originalMessageId = nonzero(originalMessageId,"originalMessageId");
        this.originDeviceId = nonzero(originDeviceId,"originDeviceId");
        this.sourceSentAtUtc = Objects.requireNonNull(sourceSentAtUtc,"sourceSentAtUtc");
        if (sourceSentAtUtc.isBefore(Instant.EPOCH))
            throw new IllegalArgumentException("Negative source UTC not allowed");
        this.content = Objects.requireNonNull(content,"content");
        if (signerSpkiSha256 == null || signerSpkiSha256.length != 32)
            throw new IllegalArgumentException("Source SPKI fingerprint must be SHA-256");
        if (originSeal == null || originSeal.length < 1 || originSeal.length > 2048)
            throw new IllegalArgumentException("Missing/oversize source seal bytes");
        if (sourceProtocolVersion <= 0)
            throw new IllegalArgumentException("Invalid source protocol version");
        this.signerSpkiSha256 = signerSpkiSha256.clone();
        this.originSeal = originSeal.clone();
        this.sourceProtocolVersion = sourceProtocolVersion;
        try {
            this.contentSha256 = MessageDigest.getInstance("SHA-256")
                .digest(content.value().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static UUID nonzero(UUID id, String label) {
        Objects.requireNonNull(id,label);
        if (ZERO.equals(id)) throw new IllegalArgumentException("Reserved "+label);
        return id;
    }
    public static HistoryEvent create(HistoryScope scope, UUID eventId, UUID originalMessageId,
                                      UUID originDeviceId, Instant sourceSentAtUtc, HistoryContent content,
                                      byte[] signerSpkiSha256, byte[] originSeal, int sourceProtocolVersion) {
        return new HistoryEvent(scope,eventId,originalMessageId,originDeviceId,sourceSentAtUtc,
                                content,signerSpkiSha256,originSeal,sourceProtocolVersion);
    }
    public HistoryScope scope(){return scope;}
    public UUID eventId(){return eventId;}
    public UUID originalMessageId(){return originalMessageId;}
    public UUID originDeviceId(){return originDeviceId;}
    public Instant sourceSentAtUtc(){return sourceSentAtUtc;}
    public HistoryContent content(){return content;}
    public byte[] signerSpkiSha256(){return signerSpkiSha256.clone();}
    public byte[] contentSha256(){return contentSha256.clone();}
    public byte[] originSeal(){return originSeal.clone();}
    public int sourceProtocolVersion(){return sourceProtocolVersion;}
}
