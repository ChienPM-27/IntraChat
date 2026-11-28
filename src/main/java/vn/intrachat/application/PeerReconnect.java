package vn.intrachat.application;

import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.transport.NetworkConfig;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Retry only previously approved private/loopback endpoints; never scan the LAN. */
public final class PeerReconnect implements AutoCloseable {
    private final UUID local;
    private final HistoryRepository history;
    private final LanCoordinator coordinator;
    private final Consumer<String> status;
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"intrachat-reconnect");t.setDaemon(true);return t;
    });
    private final Map<UUID,Long> next=new ConcurrentHashMap<>();
    private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
    private volatile boolean paused,closed;
    public PeerReconnect(UUID local,HistoryRepository history,LanCoordinator coordinator,Consumer<String> status){
        this.local=local;this.history=history;this.coordinator=coordinator;this.status=status;
    }
    public void start(){timer.scheduleWithFixedDelay(this::tick,2,2,TimeUnit.SECONDS);}
    public boolean approveKnown(LanCoordinator.Incoming incoming){
        String saved=history.peerAddress(incoming.deviceId());
        return !closed&&!paused&&incoming.reconnect()&&saved!=null&&saved.equals(incoming.ipv4());
    }
    public void connected(LanCoordinator.Peer peer){
        try{history.savePeerAddress(peer.session().remoteId(),peer.ipv4());}
        catch(IllegalArgumentException ignored){/* IPv6/public endpoints are not auto-reconnectable */}
        if(peer.reconnectEndpoint()!=null)history.savePeerEndpoint(peer.session().remoteId(),peer.reconnectEndpoint());
        next.remove(peer.session().remoteId());
    }
    public void pause(){paused=true;}
    public void resume(){paused=false;next.clear();}
    private void tick(){
        if(closed||paused)return;
        try{
            var connected=coordinator.connectedPeers();
            long now=System.currentTimeMillis();
            for(var entry:history.peerEndpoints().entrySet()){
                UUID id=entry.getKey();
                if(id.equals(local)||connected.containsKey(id)||pending.contains(id))continue;
                if(connected.size()+pending.size()>=NetworkConfig.MAX_ACTIVE_SESSIONS)break;
                // Stagger simultaneous dials by UUID, so either side can recover alone.
                long due=next.computeIfAbsent(id,k->now+(local.compareTo(id)<0?0:4000));
                if(now<due||!pending.add(id))continue;
                next.put(id,now+15000);
                coordinator.reconnect(entry.getValue(),id).whenComplete((peer,error)->{
                    pending.remove(id);
                    if(closed)return;
                    if(peer!=null){
                        if(paused)coordinator.disconnect(id);
                        else status.accept("Đã tự kết nối lại: "+peer.name());
                    }
                });
            }
        }catch(RuntimeException ignored){/* retry next tick, not on EDT */}
    }
    @Override public void close(){closed=true;timer.shutdownNow();}
}
