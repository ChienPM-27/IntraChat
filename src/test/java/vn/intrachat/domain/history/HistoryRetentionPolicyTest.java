package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRetentionPolicyTest {
    @Test void bothConfirmedNinetyDayAndFiveHundredMibDefaultThresholdsApply() {
        HistoryRetentionPolicy p=HistoryRetentionPolicy.defaults();
        assertEquals(Duration.ofDays(90),p.maxAge());
        assertEquals(500L*1024*1024,p.maxBytes());
        Instant now=Instant.parse("2026-11-21T09:00:00Z");
        assertFalse(p.evaluate(now.minus(Duration.ofDays(89)),now,100,false).requiresCleanup());
        assertTrue(p.evaluate(now.minus(Duration.ofDays(90)),now,100,false).expireByAge());
        assertTrue(p.evaluate(now,now,500L*1024*1024,false).overSize());
        assertFalse(p.evaluate(now,now,499L*1024*1024,false).requiresCleanup());
    }

    @Test void tombstonesStayProtectedAndCustomSettingsAreBounded() {
        HistoryRetentionPolicy p=HistoryRetentionPolicy.custom(Duration.ofDays(7),20L*1024*1024);
        Instant now=Instant.parse("2026-11-21T09:00:00Z");
        HistoryRetentionPolicy.Decision kept=p.evaluate(now.minus(Duration.ofDays(30)),now,30L*1024*1024,true);
        assertTrue(kept.requiresCleanup());
        assertTrue(kept.protectTombstone());
        assertFalse(kept.deleteThisEvent());
        assertThrows(IllegalArgumentException.class,()->HistoryRetentionPolicy.custom(Duration.ZERO,500L*1024*1024));
        assertThrows(IllegalArgumentException.class,()->HistoryRetentionPolicy.custom(Duration.ofDays(2),1));
        assertThrows(IllegalArgumentException.class,()->p.evaluate(now,now,-1,false));
    }
}
