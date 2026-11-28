package vn.intrachat.application;

import vn.intrachat.protocol.*;
import vn.intrachat.transport.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Human-approved plaintext TCP connections. UUID values are self-declared, not verified identities. */
public final class LanCoordinator implements AutoCloseable {
    public record Incoming(UUID deviceId, String displayName, String endpoint) {}
    public interface Approval { CompletionStage<Boolean> decide(Incoming incoming); }
    public record Peer(LanSession session, String name) {}
    private final LocalProfile local;
    private final Approval approval;
    private final Consumer<Peer> connected;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID,Peer> peers = new ConcurrentHashMap<>();
    private final WireCodec codec = new WireCodec();
    private final Semaphore candidateSlots = new Semaphore(NetworkConfig.MAX_PENDING_APPROVALS);
    private volatile ServerSocket listener;
    private volatile boolean closed;

    public LanCoordinator(LocalProfile local, Approval approval, Consumer<Peer> connected) {
        this.local=Objects.requireNonNull(local);
        this.approval=Objects.requireNonNull(approval);
        this.connected=Objects.requireNonNull(connected);
    }
    public synchronized void startListening(NetworkConfig config) throws IOException {
        if(closed) throw new IOException("Coordinator closed");
        if(listener != null) throw new IOException("Already listening");
        ServerSocket server=new ServerSocket();
        try {
            server.bind(config.socketAddress(), 8);
        } catch (IOException | RuntimeException failure) {
            try { server.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        listener=server;
        workers.submit(() -> {
            while (!server.isClosed() && !closed) {
                try {
                    Socket socket=server.accept();
                    if(!candidateSlots.tryAcquire()){socket.close();continue;}
                    workers.submit(() -> {try{accept(socket);}finally{candidateSlots.release();}});
                } catch (IOException ex) { if (!server.isClosed()) stopListening(); break; }
            }
        });
    }
    private void accept(Socket socket) {
        boolean handedOff=false;
        try {
            socket.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            WireMessage hello=codec.read(socket.getInputStream());
            if(hello.type()!=WireMessage.Type.HELLO || hello.senderId().equals(local.deviceId()))
                throw new IOException("Invalid HELLO");
            String name=hello.payload().get("displayName");
            if(name==null || name.isBlank() || name.length()>80) throw new IOException("Invalid displayName");
            boolean ok=approval.decide(new Incoming(hello.senderId(),name,
                String.valueOf(socket.getRemoteSocketAddress())))
                .toCompletableFuture().get(NetworkConfig.APPROVAL_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            pruneClosed();
            if(!ok || peers.containsKey(hello.senderId()) || peers.size()>=NetworkConfig.MAX_ACTIVE_SESSIONS){
                codec.write(socket.getOutputStream(), new WireMessage(WireMessage.Type.REJECT,null,null,
                    local.deviceId(),hello.senderId(),null,Map.of()));
                return;
            }
            establish(socket,hello.senderId(),name,new WireMessage(WireMessage.Type.WELCOME,null,null,
                local.deviceId(),hello.senderId(),null,Map.of("displayName",local.displayName())));
            handedOff=true;
        } catch (Exception ignored) { /* invalid or denied candidate */ }
        finally {if(!handedOff)try{socket.close();}catch(IOException ignored){}}
    }
    public CompletableFuture<Peer> connect(NetworkConfig target) {
        if(closed) return CompletableFuture.failedFuture(new IOException("Coordinator closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                Socket socket=new Socket();
                try {
                    socket.connect(target.socketAddress(),NetworkConfig.CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(NetworkConfig.APPROVAL_TIMEOUT_MS + NetworkConfig.HELLO_TIMEOUT_MS);
                    codec.write(socket.getOutputStream(),new WireMessage(WireMessage.Type.HELLO,null,null,
                        local.deviceId(),null,null,Map.of("displayName",local.displayName())));
                    WireMessage welcome=codec.read(socket.getInputStream());
                    if(welcome.type()==WireMessage.Type.REJECT) throw new IOException("Connection rejected");
                    if(welcome.type()!=WireMessage.Type.WELCOME || welcome.senderId().equals(local.deviceId())
                        || !local.deviceId().equals(welcome.recipientId()))
                        throw new IOException("Invalid WELCOME");
                    String name=welcome.payload().get("displayName");
                    if(name==null || name.isBlank() || name.length()>80) throw new IOException("Invalid remote name");
                    return establish(socket,welcome.senderId(),name,null);
                } catch (Exception ex) {socket.close();throw ex;}
            } catch (Exception ex) { throw new CompletionException(ex); }
        },workers);
    }
    private void pruneClosed(){peers.entrySet().removeIf(e->!e.getValue().session().isOpen());}
    private synchronized Peer establish(Socket socket, UUID remoteId,String name,WireMessage greeting) throws Exception {
        pruneClosed();
        if(peers.size()>=NetworkConfig.MAX_ACTIVE_SESSIONS || peers.containsKey(remoteId))
            throw new IOException("Duplicate peer or connection limit");
        LanSession session=new LanSession(socket,local.deviceId(),remoteId);
        Peer peer=new Peer(session,name);
        if(peers.putIfAbsent(remoteId,peer)!=null) throw new IOException("Duplicate peer");
        try {
            if(greeting!=null)session.send(greeting);
            connected.accept(peer);
        } catch (Exception error) { peers.remove(remoteId,peer);session.close();throw error; }
        return peer;
    }
    public Map<UUID,String> connectedPeers() {
        pruneClosed();
        Map<UUID,String> out=new HashMap<>();
        peers.forEach((id,p)->{if(p.session().isOpen())out.put(id,p.name());});
        return Map.copyOf(out);
    }
    public void disconnect(UUID remoteId) {
        Peer peer=peers.remove(remoteId);
        if(peer!=null)peer.session().close();
    }
    public synchronized void stopListening() {
        if(listener!=null){try{listener.close();}catch(IOException ignored){}listener=null;}
    }
    @Override public void close() {
        closed=true;stopListening();
        for(UUID id:List.copyOf(peers.keySet()))disconnect(id);
        workers.shutdownNow();
    }
}
