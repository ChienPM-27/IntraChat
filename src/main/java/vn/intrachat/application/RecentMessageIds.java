package vn.intrachat.application;

import java.util.*;

/** Per-session bounded recent-ID cache. No cross-restart exactly-once claim. */
final class RecentMessageIds {
    private final int limit;
    private final LinkedHashSet<UUID> ids=new LinkedHashSet<>();

    RecentMessageIds(int limit){
        if(limit<1)throw new IllegalArgumentException("Positive dedup window required");
        this.limit=limit;
    }
    synchronized boolean firstTime(UUID id){
        Objects.requireNonNull(id);
        if(!ids.add(id))return false;
        while(ids.size()>limit)ids.remove(ids.iterator().next());
        return true;
    }
    synchronized int size(){return ids.size();}
    synchronized void clear(){ids.clear();}
}
