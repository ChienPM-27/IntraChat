package ui.components;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * UI-derived M2 READ evidence. Means a row was visibly displayed, not human eye tracking.
 * Must only be fed the actual Swing foreground viewport geometry, never mock callbacks.
 */
public final class LiveMessageVisibilityObserver {
    public static final long MIN_VISIBLE_NS=250_000_000L;
    public record Row(String conversationId,String messageId,boolean incoming,
                      boolean showing,Rectangle bounds) {
        public Row {
            Objects.requireNonNull(conversationId);
            Objects.requireNonNull(messageId);
            Objects.requireNonNull(bounds);
        }
    }
    private final LongSupplier now;
    private final BiConsumer<String,String> onViewed;
    private final Map<String,Long> firstVisible=new HashMap<>();
    private final Set<String> emitted=new HashSet<>();

    public LiveMessageVisibilityObserver(LongSupplier now,BiConsumer<String,String> onViewed) {
        this.now=Objects.requireNonNull(now);
        this.onViewed=Objects.requireNonNull(onViewed);
    }
    public void observe(boolean live,boolean windowActive,String selectedConversation,
                        Rectangle viewport,List<Row> rows) {
        if(!live||!windowActive||selectedConversation==null||viewport==null) {
            firstVisible.clear();
            return;
        }
        Set<String> currentlyVisible=new HashSet<>();
        long time=now.getAsLong();
        for(Row row:rows){
            if(!row.incoming()||!row.showing()
                ||!selectedConversation.equals(row.conversationId())
                ||!viewport.intersects(row.bounds()))continue;
            String key=row.conversationId()+"/"+row.messageId();
            currentlyVisible.add(key);
            if(emitted.contains(key))continue;
            Long start=firstVisible.putIfAbsent(key,time);
            if(start!=null && time-start>=MIN_VISIBLE_NS) {
                emitted.add(key);
                firstVisible.remove(key);
                onViewed.accept(row.conversationId(),row.messageId());
            }
        }
        firstVisible.keySet().retainAll(currentlyVisible);
    }
    public void clear(){firstVisible.clear();emitted.clear();}
}
