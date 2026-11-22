package ui;

import listener.*;
import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import vn.intrachat.protocol.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two real mutual-TLS peers with the SAME mutable alias: never route by label,
 * and a closed verified peer stays an offline RAM draft target, not fake online.
 */
class LivePeerDirectoryTest {
    @TempDir Path root;
    private static int port()throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    private static ChatSnapshot until(AtomicReference<ChatSnapshot> latest,
                                       java.util.function.Predicate<ChatSnapshot> enough)throws Exception {
        for(int i=0;i<300;i++){
            ChatSnapshot snapshot=latest.get();
            if(snapshot!=null&&enough.test(snapshot))return snapshot;
            Thread.sleep(20);
        }
        fail("Never reached expected verified directory state");
        return null;
    }
    @Test void sameNameDifferentTlsUuidRoutesCorrectlyAndOfflinePeerRemainsOfflineDraftOnly()
        throws Exception {
        DeviceIdentity me=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity b=DeviceCertificateFactory.create(UUID.randomUUID());
        DeviceIdentity c=DeviceCertificateFactory.create(UUID.randomUUID());
        var consent=(InboundApprovalPort)r->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        var sas=(FingerprintApprovalPort)r->CompletableFuture.completedFuture(true);
        AtomicReference<ChatSnapshot> current=new AtomicReference<>();
        int tcp=port();
        try(var live=new LiveChatService(me);
            var adapter=new LiveChatAdapter(me,"Host",live,()->{});
            var host=new SecureChatCoordinator(me,"Host",
                new PinnedPeerStore(root.resolve("host.pin")),consent,sas,s->{
                    try{live.attach(s,"Tên giống nhau");}catch(Exception e){throw new IllegalStateException(e);}
                });
            var peerB=new SecureChatCoordinator(b,"Tên giống nhau",
                new PinnedPeerStore(root.resolve("b.pin")),consent,sas,s->{});
            var peerC=new SecureChatCoordinator(c,"Tên giống nhau",
                new PinnedPeerStore(root.resolve("c.pin")),consent,sas,s->{})){
            adapter.connect(new ChatClientListener(){
                @Override public void onDirectoryChanged(ChatSnapshot snapshot){current.set(snapshot);}
                @Override public void onMessageReceived(ChatMessage m){}
                @Override public void onDeliveryStateChanged(String conversationId,String messageId,DeliveryState state){}
                @Override public void onConnectionError(String note){}
            });
            host.startListening(new NetworkConfig("127.0.0.1",tcp));
            TlsChatSession connectedB=peerB.connect(new NetworkConfig("127.0.0.1",tcp))
                .get(12,TimeUnit.SECONDS);
            TlsChatSession connectedC=peerC.connect(new NetworkConfig("127.0.0.1",tcp))
                .get(12,TimeUnit.SECONDS);
            var two=until(current,s->s.conversations().size()==2);
            assertEquals(2,two.users().size());
            assertEquals("Tên giống nhau",two.users().get(0).displayName());
            assertEquals("Tên giống nhau",two.users().get(1).displayName());
            assertNotEquals(two.users().get(0).id(),two.users().get(1).id());
            UUID convB=live.conversationId(b.deviceId());
            UUID convC=live.conversationId(c.deviceId());
            assertNotNull(convB);assertNotNull(convC);assertNotEquals(convB,convC);
            ChatMessage sent=adapter.sendMessage(convB.toString(),MessageContent.text("Riêng peer B 🟦"))
                .toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertEquals(convB.toString(),sent.conversationId());
            assertEquals("Riêng peer B 🟦",connectedB.receive().payload().get("text"));
            connectedC.setReadTimeout(250);
            assertThrows(SocketTimeoutException.class,connectedC::receive,
                "Same alias must not broadcast a direct-message to peer C");
            connectedB.close();
            var after=until(current,s->s.users().size()==2
                &&s.users().stream().anyMatch(u->u.id().equals(b.deviceId().toString())&&!u.online())
                &&s.users().stream().anyMatch(u->u.id().equals(c.deviceId().toString())&&u.online()));
            assertEquals(2,after.conversations().size(),
                "Previously verified offline peer must remain a local draft target");
            assertTrue(after.previews().isEmpty(),"No stale private chat text after TLS closes");
            assertThrows(ExecutionException.class,()->adapter.sendMessage(convB.toString(),
                MessageContent.text("Không tự gửi khi offline"))
                .toCompletableFuture().get(5,TimeUnit.SECONDS));
            assertTrue(live.connectedPeers().containsKey(c.deviceId()));
            assertEquals(1,live.connectedPeers().size());
        }
    }
}
