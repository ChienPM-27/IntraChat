package vn.intrachat.application;

import vn.intrachat.domain.*;
import vn.intrachat.protocol.*;
import vn.intrachat.security.DeviceIdentity;
import vn.intrachat.transport.TlsChatSession;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * RAM-only, authenticated M2 chat service.
 * Receipts mean peer APPLICATION events; never assert real human attention.
 * View notification may only be called by the actual foreground Live UI observer.
 */
public final class LiveChatService implements AutoCloseable {
    public enum EventKind { CONNECTED, RECEIVED, SENT, STATUS_CHANGED, DISCONNECTED, FAILED }
    public record Message(UUID messageId, UUID conversationId, UUID sessionId,
            UUID senderId, UUID destinationId, Instant sentAt, String text, boolean outgoing) { }
    public record Event(EventKind kind, UUID peerId, Message message,
                        MessageAttempt.Status status, String reason) { }

    private static final int MAX_MESSAGES=500;
    private static final int MAX_MESSAGE_BYTES=2*1024*1024;
    private static final int MAX_ACTIVE=4;
    private static final int MAX_PENDING=128;
    private final DeviceIdentity local;
    private final Duration ackTimeout;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"intrachat-m2-ack-deadlines");t.setDaemon(true);return t;
    });
    private final Map<UUID,PeerSession> sessions=new ConcurrentHashMap<>();
    private final Map<UUID,MessageAttempt> attempts=new ConcurrentHashMap<>();
    // Reserve atomically before any concurrent TLS send; release once on ACK/READ/close.
    private final Semaphore pendingCapacity=new Semaphore(MAX_PENDING);
    private final Set<UUID> pendingReceiptIds=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Message> outbound=new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<Event>> listeners=new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    private static final class PeerSession {
        final TlsChatSession socket;
        final UUID conversationId;
        final String displayName;
        final List<Message> messages=Collections.synchronizedList(new ArrayList<>());
        final Map<UUID,Message> inbound=new ConcurrentHashMap<>();
        final RecentMessageIds seen=new RecentMessageIds(1024);
        final Set<UUID> viewed=ConcurrentHashMap.newKeySet();
        final ConversationRamBudget budget=new ConversationRamBudget(MAX_MESSAGES,MAX_MESSAGE_BYTES);
        PeerSession(TlsChatSession socket,UUID conversationId,String displayName) {
            this.socket=socket;this.conversationId=conversationId;this.displayName=displayName;
        }
        synchronized boolean register(UUID id) {return seen.firstTime(id);}
        synchronized void add(Message message)throws IOException {
            int size=message.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            budget.reserve(size);
            messages.add(message);
        }
    }

    public LiveChatService(DeviceIdentity local){this(local,Duration.ofSeconds(30));}
    /** Package-private fast timeout for real-TLS negative tests; production is 30s. */
    LiveChatService(DeviceIdentity local,Duration ackTimeout){
        this.local=Objects.requireNonNull(local);
        this.ackTimeout=Objects.requireNonNull(ackTimeout);
        if(ackTimeout.isZero()||ackTimeout.isNegative()
            ||ackTimeout.compareTo(Duration.ofMinutes(5))>0)
            throw new IllegalArgumentException("Invalid ACK uncertainty timeout");
    }
    public UUID localId(){return local.deviceId();}
    public void addListener(Consumer<Event> observer){listeners.add(Objects.requireNonNull(observer));}
    private void publish(EventKind kind,UUID peer,Message message,MessageAttempt.Status status,String reason){
        Event e=new Event(kind,peer,message,status,reason);
        for(var observer:listeners) {
            try{observer.accept(e);}catch(RuntimeException ignored){/* callback may not kill network worker */}
        }
    }
    /** Atomic admission: four peers * two MiB each bounds all active RAM chat to eight MiB. */
    public synchronized void attach(TlsChatSession transport,String displayName)throws Exception {
        Objects.requireNonNull(transport);
        if(closed||!transport.canChat()||!local.deviceId().equals(transport.localId()))
            throw new IllegalStateException("Cannot attach unverified TLS session");
        UUID remote=transport.remoteId();
        UUID convo=P2PConversation.idFor(local.deviceId(),local.fingerprint(),
                                          remote,transport.remoteFingerprint());
        if(sessions.size()>=MAX_ACTIVE&&!sessions.containsKey(remote))
            throw new IOException("M2_ACTIVE_PEER_CAP");
        PeerSession session=new PeerSession(transport,convo,displayName);
        PeerSession existing=sessions.putIfAbsent(remote,session);
        if(existing!=null&&existing.socket.canChat())
            throw new IOException("Duplicate active verified peer");
        if(existing!=null)sessions.put(remote,session);
        publish(EventKind.CONNECTED,remote,null,null,null);
        workers.submit(()->readLoop(remote,session));
    }
    public UUID conversationId(UUID remoteId){
        PeerSession peer=sessions.get(remoteId);
        return peer==null?null:peer.conversationId;
    }
    public Map<UUID,String> connectedPeers(){
        Map<UUID,String> peers=new LinkedHashMap<>();
        for(var e:sessions.entrySet())if(e.getValue().socket.canChat())
            peers.put(e.getKey(),e.getValue().displayName);
        return Map.copyOf(peers);
    }
    public List<Message> transcript(UUID remoteId){
        PeerSession s=sessions.get(remoteId);
        if(s==null)return List.of();
        synchronized(s.messages){return List.copyOf(s.messages);}
    }
    public MessageAttempt.Status status(UUID messageId){
        MessageAttempt attempt=attempts.get(messageId);
        return attempt==null?null:attempt.status();
    }
    /** Number of messages still awaiting authenticated delivery evidence in RAM. */
    public int pendingReceiptCount(){return pendingReceiptIds.size();}
    private void releasePendingReceipt(UUID id){
        if(pendingReceiptIds.remove(id))pendingCapacity.release();
    }
    public CompletableFuture<Message> sendText(UUID remoteId,String text){
        return CompletableFuture.supplyAsync(()->{
            try{return sendBlocking(remoteId,new ChatText(text));}
            catch(Exception failure){throw new CompletionException(failure);}
        },workers);
    }
    /**
     * Explicit retry only while one verified TLS session retains an UNCERTAIN
     * message in RAM. Replays the SAME messageId/timestamp so the remote can
     * deduplicate. Caller MUST display a duplicate-risk warning to the user.
     * Never queues an offline send or initiates a network reconnection.
     */
    public CompletableFuture<Message> retryUncertain(UUID remoteId,UUID messageId){
        Objects.requireNonNull(remoteId,"remoteId");
        Objects.requireNonNull(messageId,"messageId");
        if(closed)return CompletableFuture.failedFuture(new IOException("Live chat is closed"));
        return CompletableFuture.supplyAsync(()->{
            try{
                PeerSession peer=sessions.get(remoteId);
                Message original=outbound.get(messageId);
                MessageAttempt attempt=attempts.get(messageId);
                if(peer==null||!peer.socket.canChat()||original==null||attempt==null
                    ||attempt.status()!=MessageAttempt.Status.UNCERTAIN
                    ||!peer.conversationId.equals(original.conversationId())
                    ||!peer.socket.sessionId().equals(original.sessionId())
                    ||!remoteId.equals(original.destinationId())
                    ||!local.deviceId().equals(original.senderId()))
                    throw new IOException("Manual retry requires an uncertain message in same verified session");
                peer.socket.send(new ChatV2Message(original.messageId(),ChatV2Type.CHAT_TEXT,
                    original.sessionId(),local.deviceId(),remoteId,original.conversationId(),
                    original.sentAt(),Map.of("text",original.text())));
                publish(EventKind.STATUS_CHANGED,remoteId,original,attempt.status(),
                    "MANUAL_RETRY_SENT_DUPLICATE_POSSIBLE");
                return original;
            }catch(Exception error){throw new CompletionException(error);}
        },workers);
    }

    private Message sendBlocking(UUID remoteId,ChatText body)throws Exception{
        PeerSession peer=sessions.get(remoteId);
        if(peer==null||!peer.socket.canChat())
            throw new IOException("Not connected and verified; draft must remain local");
        if(!pendingCapacity.tryAcquire())
            throw new IOException("Too many pending message receipts");
        UUID id=UUID.randomUUID();
        pendingReceiptIds.add(id);
        MessageAttempt attempt=new MessageAttempt(id);
        attempts.put(id,attempt);
        Message localMessage=new Message(id,peer.conversationId,peer.socket.sessionId(),
            local.deviceId(),remoteId,Instant.now(),body.text(),true);
        outbound.put(id,localMessage);
        attempt.startSending();
        boolean issuedToTransport=false;
        try{
            peer.add(localMessage);
            issuedToTransport=true;
            peer.socket.send(new ChatV2Message(id,ChatV2Type.CHAT_TEXT,peer.socket.sessionId(),
                local.deviceId(),remoteId,peer.conversationId,localMessage.sentAt(),
                Map.of("text",body.text())));
            attempt.localWriteCompleted();
            publish(EventKind.SENT,remoteId,localMessage,attempt.status(),null);
            AckDeadline deadline=new AckDeadline(System::nanoTime,ackTimeout);
            deadlines.schedule(()->{
                if(deadline.markUncertainIfExpired(attempt))
                    publish(EventKind.STATUS_CHANGED,remoteId,localMessage,attempt.status(),"ACK_TIMEOUT");
            },ackTimeout.toNanos(),TimeUnit.NANOSECONDS);
            return localMessage;
        }catch(Exception error){
            if(!issuedToTransport){
                attempt.failBeforeSend();
                attempts.remove(id,attempt);
                outbound.remove(id,localMessage);
                releasePendingReceipt(id);
                publish(EventKind.FAILED,remoteId,localMessage,MessageAttempt.Status.FAILED,
                    "LOCAL_CAPACITY_OR_PREWRITE_FAILURE");
            }else{
                if(attempt.status()==MessageAttempt.Status.SENDING)
                    attempt.markUncertain(); // partial TLS write might have reached peer
                publish(EventKind.FAILED,remoteId,localMessage,attempt.status(),"SEND_UNCERTAIN");
            }
            throw error;
        }
    }
    private void readLoop(UUID remoteId,PeerSession peer){
        try{
            while(!closed&&peer.socket.canChat()){
                try {dispatch(remoteId,peer,peer.socket.receive());}
                catch(SocketTimeoutException idle){/* no auto reconnect, read poll only */}
            }
        }catch(Exception failure) {
            // Do not log user text or untrusted protected-frame bytes.
        }finally {
            sessions.remove(remoteId,peer);
            // First revoke application access and erase RAM evidence; SSL close_notify
            // may block for remote cooperation and must not delay UI DISCONNECTED.
            discardSessionLedger(remoteId,peer);
            synchronized(peer){
                peer.messages.clear();
                peer.budget.clear();
                peer.inbound.clear();
                peer.viewed.clear();
                peer.seen.clear();
            }
            publish(EventKind.DISCONNECTED,remoteId,null,null,"REMOTE_CLOSED_OR_INVALID");
            peer.socket.close();
        }
    }
    private void discardSessionLedger(UUID remoteId,PeerSession peer) {
        for(var entry:outbound.entrySet()) {
            Message message=entry.getValue();
            if(message.destinationId().equals(remoteId)
                &&message.sessionId().equals(peer.socket.sessionId())
                &&outbound.remove(entry.getKey(),message)){
                attempts.remove(entry.getKey());
                releasePendingReceipt(entry.getKey());
            }
        }
    }
    private void dispatch(UUID remoteId,PeerSession peer,ChatV2Message frame)throws Exception {
        if(!peer.conversationId.equals(frame.conversationId()))
            throw new ProtocolException("CONVERSATION_MISMATCH","Protected frame mismatched peer conversation");
        switch(frame.type()){
            case CHAT_TEXT -> {
                ChatText content=new ChatText((String)frame.payload().get("text"));
                Message accepted=peer.inbound.get(frame.messageId());
                if(accepted!=null){
                    if(!accepted.text().equals(content.text())
                        ||!accepted.sentAt().equals(frame.sentAt()))
                        throw new ProtocolException("DUPLICATE_MESSAGE_CONFLICT",
                            "A message ID cannot be reused with different content or timestamp");
                    sendAcceptedAck(peer,remoteId,accepted.messageId());
                    return; // Same-ID manual retry: ACK again, never show a second bubble
                }
                if(!peer.register(frame.messageId()))return;
                Message message=new Message(frame.messageId(),peer.conversationId,frame.sessionId(),
                    remoteId,local.deviceId(),frame.sentAt(),content.text(),false);
                peer.add(message);
                peer.inbound.put(message.messageId(),message);
                publish(EventKind.RECEIVED,remoteId,message,null,null);
                sendAcceptedAck(peer,remoteId,message.messageId());
            }
            case DELIVERY_ACK -> changeStatus(remoteId,peer,frame,false);
            case READ_RECEIPT -> changeStatus(remoteId,peer,frame,true);
            default -> throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE","Pair controls not valid after READY");
        }
    }
    private void sendAcceptedAck(PeerSession peer,UUID remoteId,UUID messageId)throws IOException {
        peer.socket.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.DELIVERY_ACK,
            peer.socket.sessionId(),local.deviceId(),remoteId,peer.conversationId,
            Instant.now(),Map.of("originalMessageId",messageId.toString(),
                "acceptedAtUTC",Instant.now().toString(),"result","ACCEPTED")));
    }
    private void changeStatus(UUID remoteId,PeerSession peer,ChatV2Message event,boolean read){
        UUID original=read ? ReadReceipt.from(event).originalMessageId()
            : DeliveryReceipt.from(event).originalMessageId();
        Message message=outbound.get(original);
        MessageAttempt attempt=attempts.get(original);
        if(message==null||attempt==null
            ||!message.destinationId().equals(remoteId)
            ||!message.conversationId().equals(peer.conversationId)
            ||!message.sessionId().equals(event.sessionId())
            ||!message.senderId().equals(local.deviceId()))return;
        try{
            MessageAttempt.Status before=attempt.status();
            if(read)attempt.onViewReceipt();
            else attempt.onDeliveryAck();
            MessageAttempt.Status after=attempt.status();
            if(after==MessageAttempt.Status.APP_ACKNOWLEDGED||after==MessageAttempt.Status.READ)
                releasePendingReceipt(original);
            if(before!=after)
                publish(EventKind.STATUS_CHANGED,remoteId,message,after,null);
        }catch(IllegalStateException ignored){
            // Invalid/unsolicited state never elevates an unrelated message.
        }
    }
    /**
     * Called ONLY after the Live Swing viewport observer has verified a real foreground
     * visible incoming message. Receipt itself does not prove human gaze.
     * No offline read receipt queue or background retry.
     */
    public boolean markViewed(UUID remoteId,UUID messageId,UUID conversationId) {
        PeerSession peer=sessions.get(remoteId);
        if(peer==null||!peer.socket.canChat()||!peer.conversationId.equals(conversationId))
            return false;
        Message received=peer.inbound.get(messageId);
        if(received==null||!received.conversationId().equals(conversationId)
            ||!received.senderId().equals(remoteId)||!received.sessionId().equals(peer.socket.sessionId()))
            return false;
        if(!peer.viewed.add(messageId))return false;
        workers.submit(()->{
            try{
                peer.socket.send(new ChatV2Message(UUID.randomUUID(),ChatV2Type.READ_RECEIPT,
                    peer.socket.sessionId(),local.deviceId(),remoteId,peer.conversationId,
                    Instant.now(),Map.of("originalMessageId",messageId.toString(),
                        "readEventId",UUID.randomUUID().toString(),"viewedAtUTC",Instant.now().toString())));
            }catch(Exception failure){
                // Deliberately do NOT enqueue/auto-replay the view receipt after disconnect.
            }
        });
        return true;
    }
    @Override public void close(){
        closed=true;
        for(PeerSession peer:sessions.values())peer.socket.close();
        sessions.clear();
        attempts.clear();outbound.clear();
        for(UUID id:Set.copyOf(pendingReceiptIds))releasePendingReceipt(id);
        deadlines.shutdownNow();
        workers.shutdownNow();
        listeners.clear();
    }
}
