package vn.intrachat.application;

import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import vn.intrachat.transport.*;
import vn.intrachat.persistence.HistoryRepository;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** TCP messaging state: ACK from accepted text; READ only from visible UI events. */
public final class LiveChatService implements AutoCloseable {
    public enum EventKind { CONNECTED, RECEIVED, SENT, STATUS_CHANGED, DISCONNECTED, FAILED }
    public record Message(UUID messageId,UUID conversationId,UUID sessionId,
            UUID senderId,UUID destinationId,Instant sentAt,String text,boolean outgoing){}
    public record Event(EventKind kind,UUID peerId,Message message,MessageAttempt.Status status,String reason){}
    private static final int MAX_ACTIVE=4,MAX_PENDING=128,MAX_MESSAGES=500,MAX_BYTES=2*1024*1024;
    private final LocalProfile local;
    private final HistoryRepository history;
    private final FileTransferService fileTransfers;
    private final CallService callService;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"intrachat-ack-timeouts");t.setDaemon(true);return t;
    });
    private final Map<UUID,Peer> peers=new ConcurrentHashMap<>();
    private final Map<UUID,MessageAttempt> attempts=new ConcurrentHashMap<>();
    private final Map<UUID,Message> outbound=new ConcurrentHashMap<>();
    private final Semaphore pending=new Semaphore(MAX_PENDING);
    private final Set<UUID> pendingIds=ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<Event>> listeners=new CopyOnWriteArrayList<>();
    private final LinkedHashMap<UUID,List<Message>> recentTranscript=new LinkedHashMap<>();
    private final ConcurrentLinkedQueue<UUID> completedIds=new ConcurrentLinkedQueue<>();
    private final Set<UUID> completedSet=ConcurrentHashMap.newKeySet();
    private final Object autoAcceptLock=new Object();
    private final Set<Path> pendingMediaTargets=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile java.util.function.BiConsumer<UUID,WireMessage> groupHandler;
    private static final class Peer {
        final LanSession socket;
        final String name;
        final UUID conversation;
        final List<Message> messages=Collections.synchronizedList(new ArrayList<>());
        final Map<UUID,Message> inbound=new ConcurrentHashMap<>();
        final Set<UUID> viewed=ConcurrentHashMap.newKeySet();
        final ConversationRamBudget budget=new ConversationRamBudget(MAX_MESSAGES,MAX_BYTES);
        Peer(LanSession socket,String name){
            this.socket=socket;this.name=name;
            this.conversation=P2PConversation.idFor(socket.localId(),socket.remoteId());
        }
        synchronized void add(Message message)throws IOException {
            budget.reserve(message.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            messages.add(message);
        }
    }
    public LiveChatService(LocalProfile profile){
        local=Objects.requireNonNull(profile);
        try{history=new HistoryRepository(local.dataDir());}
        catch(IOException error){throw new IllegalStateException("Cannot open local history",error);}
        fileTransfers=new FileTransferService(local.deviceId(),(remote,type,conversation,payload)->{
            Peer peer=peers.get(remote);
            if(peer==null||!peer.socket.isOpen())throw new IOException("Peer offline");
            peer.socket.send(new WireMessage(type,UUID.randomUUID(),conversation,
                localId(),remote,Instant.now(),payload));
        },transfer->{
            persistTransfer(transfer);
            if(isTerminalTransfer(transfer.state())&&transfer.localPath()!=null){
                pendingMediaTargets.remove(transfer.localPath().toAbsolutePath().normalize());
            }
            for(var listener:fileListeners)try{listener.accept(transfer);}catch(RuntimeException ignored){}
            if(!closed&&transfer.state()==FileTransferService.State.WAITING_ACCEPT
                &&localId().equals(transfer.recipientId())){
                autoAcceptTransfer(transfer);
            }
        });
        callService=new CallService(local.deviceId(),(remote,type,conversation,payload)->{
            Peer peer=peers.get(remote);
            if(peer==null||!peer.socket.isOpen())throw new IOException("Peer offline");
            peer.socket.send(new WireMessage(type,UUID.randomUUID(),conversation,
                localId(),remote,Instant.now(),payload));
        });
    }
    public CallService calls(){return callService;}
    public CallService.CallSession startCall(UUID peerId,CallService.Mode mode)throws IOException{
        Peer peer=peers.get(peerId);
        if(peer==null||!peer.socket.isOpen())throw new IOException("Đối phương đang ngoại tuyến");
        return callService.startCall(peerId,peer.conversation,peer.name,mode,peer.socket.remoteAddress());
    }
    private static boolean isTerminalTransfer(FileTransferService.State state){
        return state==FileTransferService.State.COMPLETED||state==FileTransferService.State.REJECTED
            ||state==FileTransferService.State.CANCELLED||state==FileTransferService.State.FAILED;
    }
    private void autoAcceptTransfer(FileTransferService.Transfer transfer){
        workers.submit(()->{
            if(closed)return;
            Path target=null;
            try{
                synchronized(autoAcceptLock){
                    if(closed)return;
                    Path mediaDir=local.mediaDir();
                    Files.createDirectories(mediaDir);
                    target=resolveUniqueMediaPath(mediaDir,transfer.fileName());
                    pendingMediaTargets.add(target);
                }
                fileTransfers.accept(transfer.transferId(),target);
            }catch(Exception ignored){
                if(target!=null)pendingMediaTargets.remove(target);
            }
        });
    }
    private Path resolveUniqueMediaPath(Path mediaDir,String rawName){
        String safeName=FileTransferService.safeFileName(rawName);
        int dot=safeName.lastIndexOf('.');
        String stem=dot>0?safeName.substring(0,dot):safeName;
        String ext=dot>0?safeName.substring(dot):"";
        Path candidate=mediaDir.resolve(safeName).toAbsolutePath().normalize();
        int index=1;
        while(Files.exists(candidate)||pendingMediaTargets.contains(candidate)){
            candidate=mediaDir.resolve(stem+" ("+index+")"+ext).toAbsolutePath().normalize();
            index++;
        }
        return candidate;
    }
    private final CopyOnWriteArrayList<Consumer<FileTransferService.Transfer>> fileListeners=new CopyOnWriteArrayList<>();
    public void addFileListener(Consumer<FileTransferService.Transfer> listener){fileListeners.add(listener);}
    public FileTransferService files(){return fileTransfers;}
    public HistoryRepository history(){return history;}
    private void persistTransfer(FileTransferService.Transfer t){
        String state=t.state().name();
        String delivery=switch(t.state()){case COMPLETED -> "DELIVERED";case FAILED,REJECTED,CANCELLED -> "FAILED";default -> "SENDING";};
        String path=t.localPath()==null?"":t.localPath().toString();
        history.appendMessage(new HistoryRepository.Entry(t.transferId(),t.conversationId(),"DIRECT",
            t.senderId(),t.recipientId(),t.sentAt(),t.messageType().name(),"",delivery,t.transferId(),
            t.fileName(),t.fileSize(),state,path));
        if(t.state()==FileTransferService.State.COMPLETED||t.state()==FileTransferService.State.FAILED
            ||t.state()==FileTransferService.State.REJECTED||t.state()==FileTransferService.State.CANCELLED
            ||t.state()==FileTransferService.State.TRANSFERRING)
            history.updateTransfer(t.transferId(),delivery,state,path);
    }
    public UUID localId(){return local.deviceId();}
    public String localName(){return local.displayName();}
    public void addListener(Consumer<Event> listener){listeners.add(listener);}
    public void onGroupFrame(java.util.function.BiConsumer<UUID,WireMessage> handler){groupHandler=handler;}
    public CompletableFuture<Void> sendGroup(UUID peerId,WireMessage.Type type,UUID groupId,Map<String,String> data){
        return CompletableFuture.runAsync(()->{
            Peer peer=peers.get(peerId);
            if(peer==null||!peer.socket.isOpen())throw new CompletionException(new IOException("Peer offline"));
            try{peer.socket.send(new WireMessage(type,UUID.randomUUID(),groupId,localId(),peerId,Instant.now(),data));}
            catch(IOException failure){throw new CompletionException(failure);}
        },workers);
    }
    private void publish(EventKind kind,UUID id,Message msg,MessageAttempt.Status state,String reason){
        if(msg!=null&&state!=null)history.updateState(msg.messageId(),state.name());
        Event event=new Event(kind,id,msg,state,reason);
        for(var listener:listeners)try{listener.accept(event);}catch(RuntimeException ignored){}
    }
    public synchronized void attach(LanSession session,String name)throws IOException {
        if(closed||!session.isOpen()||!localId().equals(session.localId()))
            throw new IOException("Invalid TCP session");
        if(peers.size()>=MAX_ACTIVE||peers.containsKey(session.remoteId()))
            throw new IOException("Duplicate peer / active limit");
        Peer peer=new Peer(session,name);
        peers.put(session.remoteId(),peer);
        history.savePeer(session.remoteId(),name);
        try {
            session.start(frame->receive(session.remoteId(),peer,frame),
                failure->detach(session.remoteId(),peer));
        } catch(RuntimeException problem){peers.remove(session.remoteId(),peer);session.close();throw problem;}
        publish(EventKind.CONNECTED,session.remoteId(),null,null,null);
    }
    private synchronized void detach(UUID id,Peer peer){
        if(peers.get(id)!=peer)return;
        remember(id,peer);
        if(!peers.remove(id,peer))return;
        for(var e:outbound.entrySet()){
            if(e.getValue().destinationId().equals(id)&&e.getValue().sessionId().equals(peer.socket.sessionId())){
                MessageAttempt attempt=attempts.get(e.getKey());
                if(attempt!=null&&(attempt.status()==MessageAttempt.Status.LOCAL_SENT
                    ||attempt.status()==MessageAttempt.Status.SENDING)){
                    try{
                        attempt.markUncertain();
                        publish(EventKind.STATUS_CHANGED,id,e.getValue(),attempt.status(),"DISCONNECTED_UNACKNOWLEDGED");
                    }catch(IllegalStateException receiptRace){/* ACK won before disconnect */}
                }
                release(e.getKey());
                completed(e.getKey());
            }
        }
        fileTransfers.disconnected(id);
        callService.disconnected(id);
        peer.socket.close();
        publish(EventKind.DISCONNECTED,id,null,null,"DISCONNECTED");
    }
    public void disconnect(UUID id){
        Peer peer=peers.get(id);
        if(peer!=null){peer.socket.close();detach(id,peer);}
    }
    public UUID conversationId(UUID id){
        Peer peer=peers.get(id);
        return peer!=null?peer.conversation:P2PConversation.idFor(localId(),id);
    }
    public Map<UUID,String> connectedPeers(){
        Map<UUID,String> result=new HashMap<>();
        peers.forEach((id,p)->{if(p.socket.isOpen())result.put(id,p.name);});
        return Map.copyOf(result);
    }
    public List<Message> transcript(UUID id){
        Peer p=peers.get(id);
        List<Message> latest;
        if(p==null)latest=List.of();
        else synchronized(p.messages){latest=List.copyOf(p.messages);}
        List<Message> loaded=new ArrayList<>();
        for(var row:history.loadMessages(conversationId(id),MAX_MESSAGES,0)){
            if(!row.messageType().equals("TEXT")||!row.conversationType().equals("DIRECT"))continue;
            loaded.add(new Message(row.messageId(),row.conversationId(),null,row.senderId(),
                row.recipientId(),row.sentAtUtc(),row.text(),row.senderId().equals(localId())));
        }
        loaded.addAll(latest);return merge(id,loaded);
    }
    private synchronized List<Message> merge(UUID id,List<Message> latest){
        var byId=new LinkedHashMap<UUID,Message>();
        for(Message m:recentTranscript.getOrDefault(id,List.of()))byId.put(m.messageId(),m);
        for(Message m:latest)byId.put(m.messageId(),m);
        var all=new ArrayList<>(byId.values());
        var kept=new ArrayList<Message>();
        int bytes=0;
        for(int i=all.size()-1;i>=0&&kept.size()<MAX_MESSAGES;i--){
            Message item=all.get(i);
            int size=item.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if(size>MAX_BYTES-bytes)break;
            bytes+=size;kept.add(item);
        }
        Collections.reverse(kept);
        return List.copyOf(kept);
    }
    private synchronized void remember(UUID id,Peer peer){
        List<Message> transcript;
        synchronized(peer.messages){transcript=merge(id,List.copyOf(peer.messages));}
        recentTranscript.remove(id);
        if(!transcript.isEmpty())recentTranscript.put(id,transcript);
        while(recentTranscript.size()>MAX_ACTIVE)
            recentTranscript.remove(recentTranscript.keySet().iterator().next());
    }
    private void completed(UUID id){
        if(!completedSet.add(id))return;
        completedIds.add(id);
        while(completedIds.size()>MAX_MESSAGES*MAX_ACTIVE){
            UUID oldest=completedIds.poll();
            if(oldest!=null){completedSet.remove(oldest);outbound.remove(oldest);attempts.remove(oldest);}
        }
    }
    public MessageAttempt.Status status(UUID id){
        MessageAttempt a=attempts.get(id);return a==null?null:a.status();
    }
    public int pendingReceiptCount(){return pendingIds.size();}
    private void release(UUID id){if(pendingIds.remove(id))pending.release();}
    private WireMessage wire(WireMessage.Type type,Peer peer,UUID id,Map<String,String> payload){
        return new WireMessage(type,id,peer.conversation,localId(),peer.socket.remoteId(),Instant.now(),payload);
    }
    public CompletableFuture<Message> sendText(UUID id,String text){
        return CompletableFuture.supplyAsync(()->{
            Peer peer=peers.get(id);
            if(peer==null||!peer.socket.isOpen())throw new CompletionException(new IOException("Peer is offline"));
            ChatText content=new ChatText(text);
            if(!pending.tryAcquire())throw new CompletionException(new IOException("Pending message limit"));
            UUID messageId=UUID.randomUUID();
            pendingIds.add(messageId);
            MessageAttempt attempt=new MessageAttempt(messageId);
            attempts.put(messageId,attempt);
            Message message=new Message(messageId,peer.conversation,peer.socket.sessionId(),
                localId(),id,Instant.now(),content.text(),true);
            outbound.put(messageId,message);
            attempt.startSending();
            boolean registered=false;
            try {
                peer.add(message);
                history.appendMessage(new HistoryRepository.Entry(messageId,peer.conversation,"DIRECT",
                    localId(),id,message.sentAt(),"TEXT",message.text(),"SENDING",null,"",0,"",""));
                registered=true;
                peer.socket.send(new WireMessage(WireMessage.Type.TEXT,messageId,peer.conversation,
                    localId(),id,message.sentAt(),Map.of("text",content.text())));
                attempt.localWriteCompleted();
                publish(EventKind.SENT,id,message,attempt.status(),null);
                deadlines.schedule(()->{
                    if(attempt.status()==MessageAttempt.Status.LOCAL_SENT){
                        attempt.markUncertain();
                        publish(EventKind.STATUS_CHANGED,id,message,attempt.status(),"ACK_TIMEOUT");
                    }
                },30,TimeUnit.SECONDS);
                return message;
            }catch(Exception ex){
                if(registered){attempt.markUncertain();}
                else{
                    attempt.failBeforeSend();release(messageId);
                    outbound.remove(messageId);attempts.remove(messageId);
                }
                publish(EventKind.FAILED,id,message,attempt.status(),ex.getMessage());
                throw new CompletionException(ex);
            }
        },workers);
    }
    /** Only replays an uncertain message on the exact original open socket. */
    public CompletableFuture<Message> retryUncertain(UUID id,UUID messageId){
        return CompletableFuture.supplyAsync(()->{
            Peer peer=peers.get(id);Message message=outbound.get(messageId);
            MessageAttempt a=attempts.get(messageId);
            if(peer==null||message==null||a==null||a.status()!=MessageAttempt.Status.UNCERTAIN
                ||!peer.socket.isOpen()||!peer.socket.sessionId().equals(message.sessionId()))
                throw new CompletionException(new IOException("Cannot retry after reconnect"));
            try {
                peer.socket.send(new WireMessage(WireMessage.Type.TEXT,message.messageId(),
                    message.conversationId(),localId(),id,message.sentAt(),Map.of("text",message.text())));
                return message;
            }catch(IOException ex){throw new CompletionException(ex);}
        },workers);
    }
    private void receive(UUID id,Peer peer,WireMessage frame){
        if(frame.type().name().startsWith("FILE_")){
            if(!peer.conversation.equals(frame.conversationId()))
                throw new IllegalArgumentException("File conversation mismatch");
            fileTransfers.handleIncoming(id,frame);
            return;
        }
        if(frame.type().name().startsWith("CALL_")){
            if(!peer.conversation.equals(frame.conversationId()))
                throw new IllegalArgumentException("Call conversation mismatch");
            callService.handleIncoming(id,peer.name,peer.socket.remoteAddress(),frame);
            return;
        }
        if(frame.type().name().startsWith("GROUP_")){
            var handler=groupHandler;
            if(handler!=null)handler.accept(id,frame);
            return;
        }
        if(!peer.conversation.equals(frame.conversationId()))
            throw new IllegalArgumentException("Conversation mismatch");
        switch(frame.type()){
            case TEXT -> {
                String text=frame.payload().get("text");
                ChatText content=new ChatText(text);
                Message existing=peer.inbound.get(frame.messageId());
                var persisted=history.find(frame.messageId());
                if(existing==null&&persisted!=null){
                    if(!persisted.conversationId().equals(peer.conversation)||!persisted.senderId().equals(id)
                        ||!persisted.text().equals(content.text())||!persisted.sentAtUtc().equals(frame.sentAtUtc()))
                        throw new IllegalArgumentException("Conflicting persisted message ID");
                    try{peer.socket.send(wire(WireMessage.Type.ACK,peer,UUID.randomUUID(),
                        Map.of("originalMessageId",frame.messageId().toString())));}
                    catch(IOException e){throw new CompletionException(e);}
                    return;
                }
                if(existing!=null){
                    if(!existing.text().equals(content.text())||!existing.sentAt().equals(frame.sentAtUtc()))
                        throw new IllegalArgumentException("Conflicting duplicate ID");
                    try{peer.socket.send(wire(WireMessage.Type.ACK,peer,UUID.randomUUID(),
                        Map.of("originalMessageId",existing.messageId().toString())));}
                    catch(IOException e){throw new CompletionException(e);}
                    return;
                }
                Message msg=new Message(frame.messageId(),peer.conversation,peer.socket.sessionId(),
                    id,localId(),frame.sentAtUtc(),content.text(),false);
                try {peer.add(msg);
                    history.appendMessage(new HistoryRepository.Entry(msg.messageId(),msg.conversationId(),"DIRECT",
                        id,localId(),msg.sentAt(),"TEXT",msg.text(),"DELIVERED",null,"",0,"",""));
                }catch(IOException e){throw new CompletionException(e);}
                peer.inbound.put(msg.messageId(),msg);
                publish(EventKind.RECEIVED,id,msg,null,null);
                try{peer.socket.send(wire(WireMessage.Type.ACK,peer,UUID.randomUUID(),
                    Map.of("originalMessageId",msg.messageId().toString())));}
                catch(IOException e){throw new CompletionException(e);}
            }
            case ACK,READ -> {
                UUID original=UUID.fromString(frame.payload().get("originalMessageId"));
                Message msg=outbound.get(original);MessageAttempt attempt=attempts.get(original);
                if(msg==null||attempt==null||!msg.destinationId().equals(id)
                    ||!msg.conversationId().equals(frame.conversationId())
                    ||!msg.sessionId().equals(peer.socket.sessionId()))return;
                var before=attempt.status();
                try{
                    if(frame.type()==WireMessage.Type.READ)attempt.onViewReceipt();
                    else attempt.onDeliveryAck();
                }catch(IllegalStateException invalid){return;}
                if(before!=attempt.status())publish(EventKind.STATUS_CHANGED,id,msg,attempt.status(),null);
                if(attempt.status()==MessageAttempt.Status.READ||attempt.status()==MessageAttempt.Status.APP_ACKNOWLEDGED)
                    {release(original);completed(original);}
            }
            default -> throw new IllegalArgumentException("Unsupported live message type");
        }
    }
    public boolean markViewed(UUID id,UUID messageId,UUID conversation){
        Peer peer=peers.get(id);
        if(peer==null||!peer.socket.isOpen()||!peer.conversation.equals(conversation)
            ||!peer.inbound.containsKey(messageId)||!peer.viewed.add(messageId))return false;
        workers.submit(()->{
            try {peer.socket.send(wire(WireMessage.Type.READ,peer,UUID.randomUUID(),
                Map.of("originalMessageId",messageId.toString())));}
            catch(IOException ignored){}
        });
        return true;
    }
    @Override public void close(){
        closed=true;
        for(UUID id:List.copyOf(peers.keySet()))disconnect(id);
        callService.close();
        fileTransfers.close();deadlines.shutdownNow();workers.shutdownNow();listeners.clear();
        history.close();
        synchronized(this){recentTranscript.clear();}
    }
}
