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
    public record Incoming(UUID deviceId, String displayName, String endpoint, String ipv4, boolean reconnect) {}
    public interface Approval { CompletionStage<Boolean> decide(Incoming incoming); }
    public record Peer(LanSession session, String name, NetworkConfig reconnectEndpoint, String ipv4) {}
    private final LocalProfile local;
    private final Approval approval;
    private final Consumer<Peer> connected;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID,Peer> peers = new ConcurrentHashMap<>();
    private final WireCodec codec = new WireCodec();
    private final Semaphore candidateSlots = new Semaphore(NetworkConfig.MAX_PENDING_APPROVALS);
    private volatile ServerSocket listener;
    private volatile int listeningPort;
    private final Set<Socket> candidates=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    public LanCoordinator(LocalProfile local, Approval approval, Consumer<Peer> connected) {
        this.local=Objects.requireNonNull(local);
        this.approval=Objects.requireNonNull(approval);
        this.connected=Objects.requireNonNull(connected);
    }
    // Mở cổng TCP và chờ peer kết nối đến.
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
        listener=server;listeningPort=config.port();
        workers.submit(() -> {
            while (!server.isClosed() && !closed) {
                try {
                    // Nhận kết nối và chuyển sang worker riêng.
                    Socket socket=server.accept();
                    if(!candidateSlots.tryAcquire()){socket.close();continue;}
                    candidates.add(socket);
                    workers.submit(() -> {try{accept(socket);}finally{candidates.remove(socket);candidateSlots.release();}});
                } catch (IOException ex) { if (!server.isClosed()) stopListening(); break; }
            }
        });
    }
    private void accept(Socket socket) {
        boolean handedOff=false;
        try {
            socket.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            // Đọc HELLO chứa thông tin thiết bị gửi yêu cầu.
            WireMessage hello=codec.read(socket.getInputStream());
            if(hello.type()!=WireMessage.Type.HELLO || hello.senderId().equals(local.deviceId()))
                throw new IOException("Invalid HELLO");
            String name=hello.payload().get("displayName");
            if(name==null || name.isBlank() || name.length()>80) throw new IOException("Invalid displayName");
            boolean reconnect=hello.recipientId()!=null;
            if(reconnect&&!local.deviceId().equals(hello.recipientId()))throw new IOException("Reconnect target UUID mismatch");
            String ip=socket.getInetAddress().getHostAddress();
            NetworkConfig endpoint=advertisedEndpoint(ip,hello,null);
            // Chờ quyết định chấp nhận hoặc từ chối kết nối.
            boolean ok=approval.decide(new Incoming(hello.senderId(),name,
                String.valueOf(socket.getRemoteSocketAddress()),ip,reconnect))
                .toCompletableFuture().get(NetworkConfig.APPROVAL_TIMEOUT_MS,TimeUnit.MILLISECONDS);
            pruneClosed();
            // Gửi REJECT nếu bị từ chối, trùng peer hoặc quá giới hạn.
            if(!ok || peers.containsKey(hello.senderId()) || peers.size()>=NetworkConfig.MAX_ACTIVE_SESSIONS){
                codec.write(socket.getOutputStream(), new WireMessage(WireMessage.Type.REJECT,null,null,
                    local.deviceId(),hello.senderId(),null,Map.of()));
                return;
            }
            // Chấp nhận kết nối bằng WELCOME.
            establish(socket,hello.senderId(),name,new WireMessage(WireMessage.Type.WELCOME,null,null,
                local.deviceId(),hello.senderId(),null,greetingPayload()),endpoint);
            handedOff=true;
        } catch (Exception ignored) { /* invalid or denied candidate */ }
        finally {if(!handedOff)try{socket.close();}catch(IOException ignored){}}
    }
    public CompletableFuture<Peer> connect(NetworkConfig target) {return connect(target,null);}
    public CompletableFuture<Peer> reconnect(NetworkConfig target,UUID expectedPeer) {
        return connect(target,Objects.requireNonNull(expectedPeer));
    }
    private CompletableFuture<Peer> connect(NetworkConfig target,UUID expectedPeer) {
        if(closed) return CompletableFuture.failedFuture(new IOException("Coordinator closed"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                Socket socket=new Socket();candidates.add(socket);
                try {
                    if(closed)throw new IOException("Coordinator closed");
                    // Thiết lập TCP trước khi bắt tay ở tầng ứng dụng.
                    socket.connect(target.socketAddress(),NetworkConfig.CONNECT_TIMEOUT_MS);
                    socket.setSoTimeout(NetworkConfig.APPROVAL_TIMEOUT_MS + NetworkConfig.HELLO_TIMEOUT_MS);
                    // Gửi HELLO để giới thiệu thiết bị với peer.
                    codec.write(socket.getOutputStream(),new WireMessage(WireMessage.Type.HELLO,null,null,
                        local.deviceId(),expectedPeer,null,greetingPayload()));
                    // Chờ WELCOME hoặc REJECT từ máy nhận.
                    WireMessage welcome=codec.read(socket.getInputStream());
                    if(welcome.type()==WireMessage.Type.REJECT) throw new IOException("Connection rejected");
                    if(welcome.type()!=WireMessage.Type.WELCOME || welcome.senderId().equals(local.deviceId())
                        || !local.deviceId().equals(welcome.recipientId()))
                        throw new IOException("Invalid WELCOME");
                    if(expectedPeer!=null&&!expectedPeer.equals(welcome.senderId()))
                        throw new IOException("Reconnect peer UUID mismatch");
                    String name=welcome.payload().get("displayName");
                    if(name==null || name.isBlank() || name.length()>80) throw new IOException("Invalid remote name");
                    return establish(socket,welcome.senderId(),name,null,advertisedEndpoint(target.ipv4(),welcome,target));
                } catch (Exception ex) {socket.close();throw ex;}
                finally{candidates.remove(socket);}
            } catch (Exception ex) { throw new CompletionException(ex); }
        },workers);
    }
    private Map<String,String> greetingPayload(){
        int port=listeningPort;
        return port==0?Map.of("displayName",local.displayName()):
            Map.of("displayName",local.displayName(),"listenPort",Integer.toString(port));
    }
    private static NetworkConfig advertisedEndpoint(String ip,WireMessage greeting,NetworkConfig fallback){
        try{
            String port=greeting.payload().get("listenPort");
            return port==null?fallback:new NetworkConfig(ip,Integer.parseInt(port));
        }catch(IllegalArgumentException malformed){return fallback;}
    }
    private void pruneClosed(){peers.entrySet().removeIf(e->!e.getValue().session().isOpen());}
    private synchronized Peer establish(Socket socket, UUID remoteId,String name,WireMessage greeting,NetworkConfig endpoint) throws Exception {
        if(closed)throw new IOException("Coordinator closed");
        pruneClosed();
        if(peers.size()>=NetworkConfig.MAX_ACTIVE_SESSIONS || peers.containsKey(remoteId))
            throw new IOException("Duplicate peer or connection limit");
        // Giữ socket đã kết nối để gửi và nhận hai chiều.
        LanSession session=new LanSession(socket,local.deviceId(),remoteId);
        Peer peer=new Peer(session,name,endpoint,socket.getInetAddress().getHostAddress());
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
        if(listener!=null){try{listener.close();}catch(IOException ignored){}listener=null;listeningPort=0;}
    }
    @Override public void close() {
        closed=true;stopListening();
        for(Socket candidate:candidates)try{candidate.close();}catch(IOException ignored){}
        for(UUID id:List.copyOf(peers.keySet()))disconnect(id);
        workers.shutdownNow();
    }
}
