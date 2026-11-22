package vn.intrachat.transport;

import vn.intrachat.application.*;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.security.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration fixture ONLY: two OS JVMs, distinct DPAPI protected identities
 * and pinned stores. Scripted approvals DO NOT replace production human SAS.
 * Outputs status markers only, never keys/chat bodies/SAS codes.
 */
public final class SecurePeerProcessHarness {
    private SecurePeerProcessHarness(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=5)throw new IllegalArgumentException("role, mode, root, port, original peer UUID");
        String role=args[0],mode=args[1];
        Path root=Path.of(args[2]);
        int port=Integer.parseInt(args[3]);
        UUID original=UUID.fromString(args[4]);
        DeviceIdentity me;
        if("client".equals(role)&&"changed-key".equals(mode))
            me=DeviceCertificateFactory.create(original);
        else me=new DeviceIdentityStore(root.resolve(role+".identity")).loadOrCreate();
        PinnedPeerStore pins=new PinnedPeerStore(root.resolve(role+".pin"));
        AtomicInteger shown=new AtomicInteger();
        InboundApprovalPort consent=req->
            CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
        FingerprintApprovalPort testSas=compare->{
            if(compare.sharedCode().isBlank())throw new IllegalStateException("Missing SAS");
            shown.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        };
        if("server".equals(role))server(mode,me,pins,consent,testSas,shown,port);
        else if("client".equals(role))client(mode,me,pins,consent,testSas,shown,port);
        else throw new IllegalArgumentException("Unknown test role");
    }
    private static void server(String mode,DeviceIdentity me,PinnedPeerStore pins,
                               InboundApprovalPort consent,FingerprintApprovalPort comparison,
                               AtomicInteger shown,int port)throws Exception{
        CountDownLatch received=new CountDownLatch(1);
        try(LiveChatService service=new LiveChatService(me);
            SecureChatCoordinator coordinator=new SecureChatCoordinator(me,"Server",
                pins,consent,comparison,session->{
                    try{service.attach(session,"Client");}
                    catch(Exception error){throw new IllegalStateException(error);}
                })){
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.RECEIVED){
                    if(!"Kiểm thử TLS hai JVM 👋".equals(event.message().text())
                        ||!event.message().senderId().equals(event.peerId())
                        ||event.message().sentAt()==null)
                        throw new IllegalStateException("Mismatched protected sender/time/UTF8 payload");
                    service.sendText(event.peerId(),"Phản hồi Việt Nam 🇻🇳");
                    received.countDown();
                }
            });
            coordinator.startListening(new NetworkConfig("127.0.0.1",port));
            System.out.println("TLS_LISTENING");System.out.flush();
            if("changed-key".equals(mode)){
                Thread.sleep(2300);
                if(received.getCount()==0)throw new IllegalStateException("Changed key delivered private user text");
                System.out.println("CHANGED_KEY_DENIED_PASS");
            }else{
                if(!received.await(9,TimeUnit.SECONDS))throw new IllegalStateException("No protected text received");
                if("first".equals(mode)&&shown.get()==0)
                    throw new IllegalStateException("Missing first-pair user-comparison callback");
                if("restart".equals(mode)&&shown.get()!=0)
                    throw new IllegalStateException("Unexpected new SAS for same pinned key");
                System.out.println("PROTECTED_RECEIVE_PASS");
                System.out.println("BIDIRECTIONAL_METADATA_PASS");
                System.out.println("COMPARISON_COUNT="+shown.get());
                System.out.flush();
                Thread.sleep(300);
            }
        }
    }
    private static void client(String mode,DeviceIdentity me,PinnedPeerStore pins,
                               InboundApprovalPort consent,FingerprintApprovalPort comparison,
                               AtomicInteger shown,int port)throws Exception {
        try(LiveChatService service=new LiveChatService(me);
            SecureChatCoordinator coordinator=new SecureChatCoordinator(me,"Client",
                pins,consent,comparison,s->{})){
            CountDownLatch answer=new CountDownLatch(1);
            service.addListener(event->{
                if(event.kind()==LiveChatService.EventKind.RECEIVED){
                    if(!"Phản hồi Việt Nam 🇻🇳".equals(event.message().text())
                        ||!event.message().senderId().equals(event.peerId())
                        ||event.message().sentAt()==null)
                        throw new IllegalStateException("Wrong protected reply metadata");
                    answer.countDown();
                }
            });
            if("changed-key".equals(mode)){
                try{
                    coordinator.connect(new NetworkConfig("127.0.0.1",port))
                        .get(7,TimeUnit.SECONDS);
                    throw new IllegalStateException("Changed-key session was mistakenly authorized");
                }catch(ExecutionException expected){
                    System.out.println("CHANGED_KEY_DENIED_PASS");
                    System.out.flush();
                }
                return;
            }
            TlsChatSession linked=coordinator.connect(new NetworkConfig("127.0.0.1",port))
                .get(12,TimeUnit.SECONDS);
            service.attach(linked,"Server");
            var sent=service.sendText(linked.remoteId(),"Kiểm thử TLS hai JVM 👋")
                .get(5,TimeUnit.SECONDS);
            boolean delivered=false;
            for(int i=0;i<180;i++){
                if(service.status(sent.messageId())==MessageAttempt.Status.APP_ACKNOWLEDGED){
                    delivered=true;break;
                }
                Thread.sleep(20);
            }
            if(!delivered)throw new IllegalStateException("Missing application ACK");
            if(!answer.await(5,TimeUnit.SECONDS))throw new IllegalStateException("No protected return message");
            if("first".equals(mode)&&shown.get()==0)
                throw new IllegalStateException("No first-pair comparison callback");
            if("restart".equals(mode)&&shown.get()!=0)
                throw new IllegalStateException("Already pinned key required re-pair unexpectedly");
            System.out.println("PROTECTED_ACK_PASS");
            System.out.println("BIDIRECTIONAL_METADATA_PASS");
            System.out.println("COMPARISON_COUNT="+shown.get());
            System.out.flush();
            Thread.sleep(250);
        }
    }
}
