import vn.intrachat.application.*;
import vn.intrachat.transport.NetworkConfig;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.concurrent.*;

/** Isolated fixture for restarting the actual desktop entrypoint. */
public class ReconnectGuiSeed {
    static int port()throws Exception{try(var socket=new ServerSocket(0)){return socket.getLocalPort();}}
    public static void main(String[] args)throws Exception{
        var a=LocalProfile.loadOrCreate(LocalProfile.userDataDir("Alice"),"Alice");
        var b=LocalProfile.loadOrCreate(LocalProfile.userDataDir("Bob"),"Bob");
        LocalProfile.rememberUser("Alice");
        int pa=port(),pb=port();while(pa==pb)pb=port();
        try(var sa=new LiveChatService(a);var sb=new LiveChatService(b);
            var ca=new LanCoordinator(a,r->CompletableFuture.completedFuture(true),peer->{
                try{sa.attach(peer.session(),peer.name());sa.history().savePeerEndpoint(peer.session().remoteId(),peer.reconnectEndpoint());}
                catch(Exception e){throw new IllegalStateException(e);}
            });
            var cb=new LanCoordinator(b,r->CompletableFuture.completedFuture(true),peer->{
                try{sb.attach(peer.session(),peer.name());sb.history().savePeerEndpoint(peer.session().remoteId(),peer.reconnectEndpoint());}
                catch(Exception e){throw new IllegalStateException(e);}
            })){
            var cfgA=new NetworkConfig("127.0.0.1",pa);var cfgB=new NetworkConfig("127.0.0.1",pb);
            ca.startListening(cfgA);cb.startListening(cfgB);
            sa.history().saveLocalListen(cfgA);sb.history().saveLocalListen(cfgB);
            cb.connect(cfgA).get(8,TimeUnit.SECONDS);
            sb.sendText(a.deviceId(),"History before restart").get(8,TimeUnit.SECONDS);
            long deadline=System.currentTimeMillis()+5000;
            while(sa.transcript(b.deviceId()).isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(20);
            if(sa.transcript(b.deviceId()).isEmpty())throw new AssertionError("No seed history");
            sa.history().flush();sb.history().flush();
        }
        Files.writeString(Path.of("ports.txt"),pa+","+pb);
        System.out.println("PASS_RECONNECT_GUI_SEED");
    }
}
