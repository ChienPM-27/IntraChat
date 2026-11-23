import vn.intrachat.application.*;
import vn.intrachat.transport.*;
import vn.intrachat.domain.MessageAttempt;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone integration runner: one OS JVM per process, real localhost TCP sockets. */
public class P1ProcessSmoke {
    static void require(boolean value,String explanation) {
        if(!value)throw new AssertionError(explanation);
    }
    static void poll(java.util.function.BooleanSupplier test)throws Exception {
        for(int i=0;i<400;i++){if(test.getAsBoolean())return;Thread.sleep(25);}
        throw new AssertionError("Timed out waiting for peer event");
    }
    public static void main(String[] args)throws Exception {
        boolean listener=args[0].equals("listen");
        int port=Integer.parseInt(args[1]);
        LocalProfile profile=LocalProfile.loadOrCreate(Path.of(args[2]));
        System.out.println("PROFILE="+profile.deviceId()+" MODE="+args[0]);
        try(LiveChatService service=new LiveChatService(profile);
            LanCoordinator coordinator=new LanCoordinator(profile,
                incoming->CompletableFuture.completedFuture(true),
                peer->{try{service.attach(peer.session(),peer.name());}
                    catch(Exception exception){throw new IllegalStateException(exception);}})) {
            if(listener){
                coordinator.startListening(new NetworkConfig("127.0.0.1",port));
                System.out.println("LISTENING");
                poll(()->!service.connectedPeers().isEmpty());
                UUID remote=service.connectedPeers().keySet().iterator().next();
                poll(()->!service.transcript(remote).isEmpty());
                var incoming=service.transcript(remote).get(0);
                require(incoming.text().equals("Xin chào 👨‍👩‍👧 🇻🇳"),"Unicode mismatch");
                require(incoming.senderId().equals(remote),"Sender mismatch");
                require(incoming.sentAt()!=null,"Missing timestamp");
                require(service.markViewed(remote,incoming.messageId(),incoming.conversationId()),"READ not emitted");
                service.sendText(remote,"Đã nhận 🧑‍💻").get(8,TimeUnit.SECONDS);
                Thread.sleep(700);
                System.out.println("PASS_LISTENER");
            } else {
                var peer=coordinator.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
                UUID remote=peer.session().remoteId();
                var sent=service.sendText(remote,"Xin chào 👨‍👩‍👧 🇻🇳").get(8,TimeUnit.SECONDS);
                poll(()->service.status(sent.messageId())==MessageAttempt.Status.READ);
                poll(()->!service.transcript(remote).stream().filter(m->!m.outgoing()).toList().isEmpty());
                require(service.transcript(remote).stream().anyMatch(m->m.text().equals("Đã nhận 🧑‍💻")),"Response mismatch");
                System.out.println("PASS_CONNECTOR");
            }
        }
    }
}
