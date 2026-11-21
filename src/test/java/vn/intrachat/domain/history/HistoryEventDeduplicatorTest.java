package vn.intrachat.domain.history;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoryEventDeduplicatorTest {
    static HistoryEvent event(HistoryScope scope, UUID id, UUID source, String text, int key) {
        byte[] pin=new byte[32]; pin[0]=(byte)key;
        return HistoryEvent.create(scope,id,UUID.randomUUID(),source,Instant.parse("2026-11-21T09:00:00Z"),
            HistoryContent.text(text),pin,new byte[]{5},2);
    }

    @Test void duplicateRequiresSameSourceAndImmutableFields() {
        HistoryScope scope=HistoryScope.p2p(UUID.randomUUID());
        UUID id=UUID.randomUUID(),source=UUID.randomUUID();
        HistoryEvent original=event(scope,id,source,"hello",1);
        assertEquals(HistoryConflictDecision.INSERT,HistoryEventIdentity.classify(null,original));
        assertEquals(HistoryConflictDecision.DUPLICATE,HistoryEventIdentity.classify(original,original));
        assertEquals(HistoryConflictDecision.QUARANTINE,HistoryEventIdentity.classify(original,event(scope,id,source,"tampered",1)));
        assertEquals(HistoryConflictDecision.QUARANTINE,HistoryEventIdentity.classify(original,event(scope,id,source,"hello",2)));
        assertEquals(HistoryConflictDecision.QUARANTINE,HistoryEventIdentity.classify(original,event(scope,id,UUID.randomUUID(),"hello",1)));
        assertEquals(HistoryConflictDecision.INSERT,
            HistoryEventIdentity.classify(original,event(HistoryScope.group(scope.scopeId()),id,source,"hello",1)));
    }
}
