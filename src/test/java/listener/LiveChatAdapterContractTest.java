package listener;

import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;
import javax.swing.*;
import java.net.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real verified TLS events mapped to Swing FE; never demo timers or fake Seen. */
class LiveChatAdapterContractTest {
    @TempDir Path temp;
    private static int port()throws Exception{
        try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return socket.getLocalPort();
        }
    }
    @Test void initialDirectoryCallbackRunsOnEdtAndLiveRejectsDemoAndFutureFeatures()throws Exception{
        var id=DeviceCertificateFactory.create(UUID.randomUUID());
        try(var service=new LiveChatService(id);
            var live=new LiveChatAdapter(id,"Local verified",service,()->{})){
            CountDownLatch directory=new CountDownLatch(1);
            AtomicBoolean onEdt=new AtomicBoolean();
            live.connect(new ChatClientListener(){
                @Override public void onDirectoryChanged(ChatSnapshot list){
                    onEdt.set(SwingUtilities.isEventDispatchThread());
                    assertTrue(list.conversations().isEmpty());
                    directory.countDown();
                }
                @Override public void onMessageReceived(ChatMessage message){fail("No mock message in Live");}
                @Override public void onDeliveryStateChanged(String c,String m,DeliveryState s){
                    fail("No fake receipt in Live");}
                @Override public void onConnectionError(String text){}
            });
            assertTrue(directory.await(4,TimeUnit.SECONDS));
            assertTrue(onEdt.get(),"FE directory callback must execute on Swing EDT");
            assertTrue(live.liveMode());
            assertThrows(ExecutionException.class,()->live.signIn("demo",new char[]{'1'})
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.createGroup("Future",List.of("a"))
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.loadHistory("direct-mai")
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.sendMessage(UUID.randomUUID().toString(),
                MessageContent.text("offline must stay a RAM draft"))
                .toCompletableFuture().get(3,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->live.retryMessage(UUID.randomUUID().toString(),
                UUID.randomUUID().toString()).toCompletableFuture().get(3,TimeUnit.SECONDS));
        }
    }
    @Test void liveDirectorySendAndAuthenticatedAckEventsAlwaysOnEdt()throws Exception{
        var a=DeviceCertificateFactory.create(UUID.randomUUID());
        var b=DeviceCertificateFactory.create(UUID.randomUUID());
        var consent=(InboundApprovalPort)req->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        var human=(FingerprintApprovalPort)req->CompletableFuture.completedFuture(true);
        int port=port();
        CountDownLatch joined=new CountDownLatch(1);
        CountDownLatch bubble=new CountDownLatch(1);
        CountDownLatch delivered=new CountDownLatch(1);
        AtomicBoolean nonEdt=new AtomicBoolean(false);
        try(var service=new LiveChatService(a);
            var adapter=new LiveChatAdapter(a,"A",service,()->{});
            var server=new SecureChatCoordinator(a,"A",new PinnedPeerStore(temp.resolve("a.pin")),
                consent,human,s->{
                    try{service.attach(s,"B");}
                    catch(Exception error){throw new IllegalStateException(error);}
                });
            var client=new SecureChatCoordinator(b,"B",new PinnedPeerStore(temp.resolve("b.pin")),
                consent,human,s->{})){
            adapter.connect(new ChatClientListener(){
                private void edt(){if(!SwingUtilities.isEventDispatchThread())nonEdt.set(true);}
                @Override public void onDirectoryChanged(ChatSnapshot snapshot){
                    edt();if(!snapshot.conversations().isEmpty())joined.countDown();
                }
                @Override public void onMessageReceived(ChatMessage message){
                    edt();if(message.senderId().equals(a.deviceId().toString()))bubble.countDown();
                }
                @Override public void onDeliveryStateChanged(String c,String id,DeliveryState s){
                    edt();
                    if(s==DeliveryState.DELIVERED)delivered.countDown();
                    if(s==DeliveryState.SEEN)fail("ACK must not fake READ in Live");
                }
                @Override public void onConnectionError(String message){edt();}
            });
            server.startListening(new NetworkConfig("127.0.0.1",port));
            TlsChatSession peer=client.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            assertTrue(joined.await(4,TimeUnit.SECONDS));
            UUID convo=service.conversationId(b.deviceId());
            assertNotNull(convo);
            ChatMessage sent=adapter.sendMessage(convo.toString(),MessageContent.text("Xin chào 👋"))
                .toCompletableFuture().get(5,TimeUnit.SECONDS);
            assertTrue(bubble.await(4,TimeUnit.SECONDS));
            ChatV2Message actual=peer.receive();
            assertEquals(ChatV2Type.CHAT_TEXT,actual.type());
            assertEquals(sent.id(),actual.messageId().toString());
            assertEquals(a.deviceId().toString(),sent.senderId());
            assertEquals(1,adapter.loadHistory(convo.toString()).toCompletableFuture()
                .get(3,TimeUnit.SECONDS).size());
            peer.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
                peer.sessionId(),b.deviceId(),a.deviceId(),convo,Instant.now(),
                Map.of("originalMessageId",actual.messageId().toString(),
                    "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED")));
            assertTrue(delivered.await(5,TimeUnit.SECONDS));
            assertFalse(nonEdt.get(),"Every Live adapter notification must be marshalled onto EDT");
        }
    }
}
