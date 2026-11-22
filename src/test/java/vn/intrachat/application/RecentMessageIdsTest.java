package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Bounded dedup cache: only session-local replay protection, never persisted History. */
class RecentMessageIdsTest {
    @Test void detectsSameIdWithinOneThousandTwentyFourWindowAndEvictsEldest(){
        var cache=new RecentMessageIds(1024);
        UUID oldest=UUID.randomUUID();
        assertTrue(cache.firstTime(oldest));
        assertFalse(cache.firstTime(oldest));
        for(int i=1;i<1024;i++)assertTrue(cache.firstTime(UUID.randomUUID()));
        assertEquals(1024,cache.size());
        UUID newest=UUID.randomUUID();
        assertTrue(cache.firstTime(newest));
        assertEquals(1024,cache.size());
        assertFalse(cache.firstTime(newest));
        assertTrue(cache.firstTime(oldest),"Old ID falls out of the bounded window");
        assertEquals(1024,cache.size());
    }
    @Test void resetOnSessionCloseErasesAllIds(){
        var cache=new RecentMessageIds(1024);
        UUID id=UUID.randomUUID();
        assertTrue(cache.firstTime(id));
        cache.clear();
        assertEquals(0,cache.size());
        assertTrue(cache.firstTime(id));
        assertThrows(IllegalArgumentException.class,()->new RecentMessageIds(0));
    }
}
