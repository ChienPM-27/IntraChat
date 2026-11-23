package listener;

import model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.application.*;
import vn.intrachat.transport.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class LiveChatAdapterP1Test {
    @TempDir Path temp;
    private static void until(java.util.function.BooleanSupplier check)throws Exception {
        long limit=System.nanoTime()+Duration.ofSeconds(8).toNanos();
        while(System.nanoTime()<limit){if(check.getAsBoolean())return;Thread.sleep(20);}
        fail("Timed out waiting for adapter event");
    }
    private static final class Listener implements ChatClientListener {
        volatile ChatSnapshot snapshot;
        final CopyOnWriteArrayList<ChatMessage> messages=new CopyOnWriteArrayList<>();
        final ConcurrentHashMap<String,DeliveryState> updates=new ConcurrentHashMap<>();
        final AtomicBoolean eventsOnEdt=new AtomicBoolean(true);
        @Override public void onDirectoryChanged(ChatSnapshot s){
            eventsOnEdt.compareAndSet(true,SwingUtilities.isEventDispatchThread());
            snapshot=s;
        }
        @Override public void onMessageReceived(ChatMessage msg){
            eventsOnEdt.compareAndSet(true,SwingUtilities.isEventDispatchThread());
            messages.add(msg);
        }
        @Override public void onDeliveryStateChanged(String conversation,String messageId,DeliveryState state){
            eventsOnEdt.compareAndSet(true,SwingUtilities.isEventDispatchThread());
            updates.put(messageId,state);
        }
        @Override public void onConnectionError(String message){
            eventsOnEdt.compareAndSet(true,SwingUtilities.isEventDispatchThread());
        }
    }
    @Test void realTcpEventsReachSingleMessengerAdapterOnEdt()throws Exception {
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("A"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("B"));
        int port;
        try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
        try(LiveChatService sa=new LiveChatService(a);
            LiveChatService sb=new LiveChatService(b);
            LanCoordinator ca=new LanCoordinator(a,i->CompletableFuture.completedFuture(true),p->{
                try{sa.attach(p.session(),p.name());}catch(Exception ex){throw new IllegalStateException(ex);}
            });
            LanCoordinator cb=new LanCoordinator(b,i->CompletableFuture.completedFuture(true),p->{
                try{sb.attach(p.session(),p.name());}catch(Exception ex){throw new IllegalStateException(ex);}
            })){
            LiveChatAdapter adapterA=new LiveChatAdapter(a,a.displayName(),sa,()->{});
            LiveChatAdapter adapterB=new LiveChatAdapter(b,b.displayName(),sb,()->{});
            Listener left=new Listener(),right=new Listener();
            adapterA.connect(left);adapterB.connect(right);
            ca.startListening(new NetworkConfig("127.0.0.1",port));
            cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            until(()->left.snapshot!=null&&!left.snapshot.conversations().isEmpty()
                &&right.snapshot!=null&&!right.snapshot.conversations().isEmpty());
            String conversation=left.snapshot.conversations().get(0).id();
            assertEquals(conversation,right.snapshot.conversations().get(0).id());
            ChatMessage sent=adapterB.sendMessage(conversation,MessageContent.text("Chào 👩‍💻")).toCompletableFuture()
                .get(8,TimeUnit.SECONDS);
            until(()->left.messages.stream().anyMatch(m->m.id().equals(sent.id())));
            ChatMessage received=left.messages.stream().filter(m->m.id().equals(sent.id())).findFirst().orElseThrow();
            assertEquals(b.deviceId().toString(),received.senderId());
            assertEquals(conversation,received.conversationId());
            assertEquals("Chào 👩‍💻",received.content().text());
            assertNotNull(received.sentAt());
            until(()->right.updates.get(sent.id())==DeliveryState.DELIVERED);
            // Simulate the explicit viewport notification; not a claim of GUI manual test.
            adapterA.onMessageVisible(conversation,sent.id());
            until(()->right.updates.get(sent.id())==DeliveryState.SEEN);
            assertTrue(left.eventsOnEdt.get());
            assertTrue(right.eventsOnEdt.get());
        }
    }
}
