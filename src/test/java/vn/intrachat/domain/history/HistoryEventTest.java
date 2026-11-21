package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoryEventTest {
    static HistoryEvent event(HistoryContent content, byte[] pin, byte[] seal) {
        return HistoryEvent.create(HistoryScope.p2p(UUID.randomUUID()),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-11-21T09:00:00Z"),content,pin,seal,2);
    }

    @Test void preservesUnicodeAndSourceIdentityAndDefendsBytes() {
        byte[] pin=new byte[32],seal={1,2,3};
        pin[0]=8;
        HistoryContent content=HistoryContent.text("Chào 👩‍💻\nXin chào");
        HistoryEvent e=event(content,pin,seal);
        pin[0]=7;seal[0]=9;
        assertEquals("Chào 👩‍💻\nXin chào",e.content().value());
        assertEquals(8,e.signerSpkiSha256()[0]);
        assertEquals(1,e.originSeal()[0]);
        byte[] out=e.originSeal();out[0]=88;
        assertEquals(1,e.originSeal()[0]);
        assertEquals(32,e.contentSha256().length);
        assertEquals(2,e.sourceProtocolVersion());
        assertEquals(Instant.parse("2026-11-21T09:00:00Z"),e.sourceSentAtUtc());
    }

    @Test void validatesTypesBoundsAndNeverTreatsDraftAsMessage() {
        assertThrows(IllegalArgumentException.class,()->HistoryContent.text(""));
        assertThrows(IllegalArgumentException.class,()->HistoryContent.text("a".repeat(8001)));
        assertThrows(IllegalArgumentException.class,()->HistoryContent.emoji("x".repeat(9000)));
        assertThrows(IllegalArgumentException.class,()->HistoryContent.text("broken \uD800"));
        assertThrows(IllegalArgumentException.class,()->event(HistoryContent.text("hi"),new byte[31],new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->event(HistoryContent.text("hi"),new byte[32],new byte[0]));
        assertThrows(IllegalArgumentException.class,()->HistoryEvent.create(
            HistoryScope.p2p(UUID.randomUUID()),new UUID(0,0),UUID.randomUUID(),UUID.randomUUID(),
            Instant.now(),HistoryContent.text("hi"),new byte[32],new byte[]{1},2));
        assertThrows(IllegalArgumentException.class,()->HistoryEvent.create(
            HistoryScope.p2p(UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
            Instant.now(),HistoryContent.text("hi"),new byte[32],new byte[]{1},0));
    }
}
