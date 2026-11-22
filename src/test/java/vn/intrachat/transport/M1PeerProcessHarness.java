package vn.intrachat.transport;

import vn.intrachat.application.*;
import vn.intrachat.domain.PeerIdentity;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone test-only real JVM process. No production auto-consent:
 * the accepted/rejected decision is an explicit scripted test fixture.
 */
public final class M1PeerProcessHarness {
    private static final UUID A=UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID B=UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("usage: server|client accept|reject|timeout port");
        int port=Integer.parseInt(args[2]);
        if("server".equals(args[0]))server(args[1],port);
        else if("client".equals(args[0]))client(args[1],port);
        else throw new IllegalArgumentException("Unknown fixture role");
    }
    private static void server(String decision,int port)throws Exception{
        var connected=new CountDownLatch(1);
        var incoming=new CountDownLatch(1);
        var remote=new AtomicReference<UUID>();
        InboundApprovalPort approval=request->{
            incoming.countDown();
            if("accept".equals(decision))return CompletableFuture.completedFuture(InboundApprovalPort.Decision.ACCEPT);
            if("reject".equals(decision))return CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
            if("timeout".equals(decision))return new CompletableFuture<InboundApprovalPort.Decision>()
                .completeOnTimeout(InboundApprovalPort.Decision.REJECT,200,TimeUnit.MILLISECONDS);
            throw new IllegalArgumentException("Bad fixture response");
        };
        try(var node=new NetworkCoordinator(new PeerIdentity(A,"Server test only"),approval,event->{
            if(event.kind()==NetworkEvents.Kind.CONNECTED){
                remote.set(event.remoteDeviceId());
                connected.countDown();
            }
        })){
            node.startListening(new NetworkConfig("127.0.0.1",port));
            System.out.println("LISTENING");
            System.out.flush();
            if(!incoming.await(7,TimeUnit.SECONDS))throw new IllegalStateException("No incoming invitation");
            if("accept".equals(decision)){
                if(!connected.await(7,TimeUnit.SECONDS))throw new IllegalStateException("Not ready after consent");
                node.ping(remote.get()).get(5,TimeUnit.SECONDS);
                System.out.println("A_PING_PASS");
                System.out.flush();
                Thread.sleep(1200); // keep socket available while client finishes its probe
            }else{
                Thread.sleep(350);
                if(node.connectedCount()!=0)throw new IllegalStateException("Deny/expiry became CONNECTED");
                System.out.println("A_DENIED_PASS");
                System.out.flush();
            }
        }
    }
    private static void client(String decision,int port)throws Exception {
        InboundApprovalPort deny=req->CompletableFuture.completedFuture(InboundApprovalPort.Decision.REJECT);
        try(var node=new NetworkCoordinator(new PeerIdentity(B,"Client test only"),deny,e->{})){
            if("accept".equals(decision)){
                if(!A.equals(node.connect(new NetworkConfig("127.0.0.1",port))
                    .get(7,TimeUnit.SECONDS).deviceId()))throw new IllegalStateException("Remote self-declared ID mismatch");
                node.ping(A).get(5,TimeUnit.SECONDS);
                System.out.println("B_PING_PASS");
                System.out.flush();
                Thread.sleep(300);
            }else{
                try{
                    node.connect(new NetworkConfig("127.0.0.1",port)).get(7,TimeUnit.SECONDS);
                    throw new IllegalStateException("Denied invitation unexpectedly connected");
                }catch(ExecutionException expected){
                    if(node.connectedCount()!=0)throw new IllegalStateException("Denied attempt leaked session");
                    System.out.println("B_DENIED_PASS");
                    System.out.flush();
                }
            }
        }
    }
}
