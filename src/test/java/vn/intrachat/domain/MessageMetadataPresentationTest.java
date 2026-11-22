package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import vn.intrachat.application.ChatViewMapper;
import vn.intrachat.application.LiveChatService;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Timestamp is an instant, not global causal ordering. Display name never authenticates. */
class MessageMetadataPresentationTest {
    private static final UUID LOCAL=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PEER=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static LiveChatService.Message from(UUID sender,Instant when,String text){
        return new LiveChatService.Message(UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),sender,sender.equals(LOCAL)?PEER:LOCAL,
            when,text,!sender.equals(PEER));
    }
    @Test void exactUtcInstantIsPreservedAcrossTimeZonesAndLocale() {
        Instant time=Instant.parse("2026-11-22T06:45:30Z");
        var item=from(PEER,time,"Chào 👋");
        var hcm=ChatViewMapper.present(item,LOCAL,PEER,"Tên bạn thay đổi",
            ZoneId.of("Asia/Ho_Chi_Minh"),Locale.forLanguageTag("vi-VN"));
        var nyc=ChatViewMapper.present(item,LOCAL,PEER,"Changed alias",
            ZoneId.of("America/New_York"),Locale.US);
        assertEquals(time,hcm.utcInstant());
        assertEquals(time,nyc.utcInstant());
        assertEquals(LocalDateTime.of(2026,11,22,13,45,30),hcm.localDateTime());
        assertEquals(LocalDateTime.of(2026,11,22,1,45,30),nyc.localDateTime());
        assertEquals(PEER,hcm.verifiedSenderId());
        assertEquals(PEER,nyc.verifiedSenderId());
        assertFalse(hcm.outgoing());
        assertFalse(nyc.outgoing());
        assertEquals("Chào 👋",hcm.text());
    }
    @Test void mutableAliasCannotChangeVerifiedSenderOrDirection() {
        var item=from(PEER,Instant.parse("2026-01-01T00:00:00Z"),"x");
        var one=ChatViewMapper.present(item,LOCAL,PEER,"Alice",ZoneOffset.UTC,Locale.US);
        var two=ChatViewMapper.present(item,LOCAL,PEER,"Bob",ZoneOffset.UTC,Locale.US);
        assertEquals(one.verifiedSenderId(),two.verifiedSenderId());
        assertFalse(one.outgoing());
        assertEquals("Alice",one.displayAlias());
        assertEquals("Bob",two.displayAlias());
        var outgoing=ChatViewMapper.present(
            from(LOCAL,Instant.parse("2026-01-01T00:00:00Z"),"Hi"),LOCAL,PEER,"Alias",
            ZoneOffset.UTC,Locale.US);
        assertTrue(outgoing.outgoing());
    }
    @Test void skewedRemoteTimeNeverBecomesAuthoritativeOrTrustProof() {
        var future=ChatViewMapper.present(from(PEER,Instant.parse("2099-01-01T00:00:00Z"),"Hi"),
            LOCAL,PEER,"Peer",ZoneOffset.UTC,Locale.US);
        assertTrue(future.clockSkewWarning(Instant.parse("2026-11-22T00:00:00Z"),
            Duration.ofMinutes(5)));
        assertFalse(future.clockSkewWarning(Instant.parse("2099-01-01T00:00:00Z"),
            Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class,()->ChatViewMapper.present(
            from(UUID.randomUUID(),Instant.now(),"spoof"),LOCAL,PEER,"Outsider",
            ZoneOffset.UTC,Locale.US));
    }
}
