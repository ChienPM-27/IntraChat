package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.transport.NetworkConfig;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class PeerReconnectTest {
    @TempDir Path temp;
    static int port() throws Exception {try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    static void until(BooleanSupplier condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(22);
        while(System.nanoTime()<deadline){if(condition.getAsBoolean())return;Thread.sleep(25);}
        fail("Reconnect timeout");
    }
    static final class Node implements AutoCloseable {
        final LocalProfile profile;
        final LiveChatService live;
        final GroupService groups;
        final LanCoordinator lan;
        final PeerReconnect reconnect;
        final AtomicInteger manual=new AtomicInteger();
        Node(Path dir)throws Exception{
            profile=LocalProfile.loadOrCreate(dir);
            live=new LiveChatService(profile);
            groups=new GroupService(live,(owner,name,title)->CompletableFuture.completedFuture(true));
            lan=new LanCoordinator(profile,this::approve,this::connected);
            reconnect=new PeerReconnect(profile.deviceId(),live.history(),lan,s->{});
            live.addListener(e->{if(e.kind()==LiveChatService.EventKind.DISCONNECTED)lan.disconnect(e.peerId());});
        }
        private CompletionStage<Boolean> approve(LanCoordinator.Incoming incoming){
            if(incoming.reconnect())return CompletableFuture.completedFuture(reconnect.approveKnown(incoming));
            manual.incrementAndGet();return CompletableFuture.completedFuture(true);
        }
        private void connected(LanCoordinator.Peer p){
            try{live.attach(p.session(),p.name());reconnect.connected(p);}
            catch(Exception e){throw new IllegalStateException(e);}
        }
        UUID id(){return profile.deviceId();}
        void listen(int port)throws Exception{
            var config=new NetworkConfig("127.0.0.1",port);
            lan.startListening(config);live.history().saveLocalListen(config);
        }
        public void close(){reconnect.close();lan.close();groups.close();live.close();}
    }
    @Test void restartBothProfilesOpensKnownPeerWithoutManualApprovalAndRestoresGroup()throws Exception{
        Path aDir=temp.resolve("A"),bDir=temp.resolve("B");
        int aPort=port(),bPort=port();
        while(aPort==bPort)bPort=port();
        UUID aId,bId,group;
        try(Node a=new Node(aDir);Node b=new Node(bDir)){
            aId=a.id();bId=b.id();
            a.listen(aPort);b.listen(bPort);
            b.lan.connect(new NetworkConfig("127.0.0.1",aPort)).get(8,TimeUnit.SECONDS);
            until(()->a.live.history().peerEndpoints().containsKey(b.id())&&b.live.history().peerEndpoints().containsKey(a.id()));
            assertEquals(1,a.manual.get());
            assertEquals(bPort,a.live.history().peerEndpoints().get(bId).port());
            assertEquals(aPort,b.live.history().peerEndpoints().get(aId).port());
            b.live.sendText(a.id(),"old history").get(8,TimeUnit.SECONDS);
            until(()->!a.live.transcript(bId).isEmpty());
            group=a.groups.createGroup("Restore",List.of(b.id())).toCompletableFuture().get(8,TimeUnit.SECONDS).groupId();
            until(()->a.groups.ready(group)&&b.groups.ready(group)&&b.groups.group(group).memberIds().size()==2);
            a.live.history().flush();b.live.history().flush();
        }
        try(Node a=new Node(aDir);Node b=new Node(bDir)){
            assertEquals(aId,a.id());assertEquals(bId,b.id());
            assertTrue(a.live.connectedPeers().isEmpty());
            assertEquals(aPort,a.live.history().localListen().port());
            assertEquals(bPort,b.live.history().localListen().port());
            assertTrue(a.live.transcript(bId).stream().anyMatch(m->m.text().equals("old history")));
            assertFalse(b.groups.ready(group));
            a.listen(aPort);a.reconnect.start(); // B is initially offline
            Thread.sleep(2500);
            assertTrue(a.live.connectedPeers().isEmpty());
            b.listen(bPort);b.reconnect.start();
            until(()->a.live.connectedPeers().containsKey(bId)&&b.live.connectedPeers().containsKey(aId)&&b.groups.ready(group));
            assertEquals(0,a.manual.get());assertEquals(0,b.manual.get());
            var sent=b.groups.sendGroupText(group,"group after reconnect").toCompletableFuture().get(8,TimeUnit.SECONDS);
            until(()->a.groups.history(group).stream().anyMatch(m->m.messageId().equals(sent.messageId())));
            var direct=b.live.sendText(aId,"direct after reconnect").get(8,TimeUnit.SECONDS);
            until(()->a.live.transcript(bId).stream().anyMatch(m->m.messageId().equals(direct.messageId())));
            Path sample=temp.resolve("sample.bin");Files.write(sample,new byte[]{1,2,3});
            var media=b.groups.sendGroupMedia(group,sample,model.MessageType.FILE);
            until(()->a.live.history().find(media.transferId())!=null&&
                a.live.history().find(media.transferId()).transferState().equals("COMPLETED")&&
                b.live.history().find(media.transferId()).transferState().equals("COMPLETED"));
            assertArrayEquals(new byte[]{1,2,3},Files.readAllBytes(Path.of(a.live.history().find(media.transferId()).localPath())));
            a.reconnect.pause();b.reconnect.pause();a.live.disconnect(bId);
            until(()->b.live.connectedPeers().isEmpty());
            Thread.sleep(2500);
            assertTrue(a.live.connectedPeers().isEmpty());assertTrue(b.live.connectedPeers().isEmpty());
        }
    }
    @Test void unknownAutoReconnectAndWrongExpectedUuidAreRejectedWithoutPrompt()throws Exception{
        try(Node a=new Node(temp.resolve("A"));Node b=new Node(temp.resolve("B"))){
            int port=port();a.listen(port);
            NetworkConfig target=new NetworkConfig("127.0.0.1",port);
            assertThrows(ExecutionException.class,()->b.lan.reconnect(target,a.id()).get(8,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->b.lan.reconnect(target,UUID.randomUUID()).get(8,TimeUnit.SECONDS));
            assertEquals(0,a.manual.get());
            assertTrue(a.live.connectedPeers().isEmpty());
            assertTrue(b.live.connectedPeers().isEmpty());
            b.lan.connect(target).get(8,TimeUnit.SECONDS);
            until(()->a.live.connectedPeers().containsKey(b.id()));
            assertEquals(1,a.manual.get());
        }
    }
}
