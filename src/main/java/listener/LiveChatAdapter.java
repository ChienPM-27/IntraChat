package listener;

import model.*;
import vn.intrachat.application.LiveChatService;
import vn.intrachat.application.ChatViewMapper;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.security.DeviceIdentity;

import java.util.*;
import java.util.concurrent.*;

/**
 * Messenger FE Live adapter backed by protected socket events and RAM-only transcripts.
 * No demo login, seeded conversations, artificial delivery/read timers or persisted History.
 */
public final class LiveChatAdapter implements ChatActionHandler {
    private final DeviceIdentity local;
    private final LiveChatService service;
    private final ChatUser self;
    private final Runnable closeNetwork;
    private final Runnable connectionSettings;
    // In-process only: verified peer IDs/aliases for offline RAM drafts. No chat
    // bodies, keys, receipts or persistent contact/History storage.
    private final LinkedHashMap<UUID,PeerLabel> recentVerified=new LinkedHashMap<>();
    private static final int MAX_RECENT_PEERS=32;
    private record PeerLabel(UUID conversationId,String alias){}
    private volatile ChatClientListener subscriber;
    private volatile boolean closed;

    public LiveChatAdapter(DeviceIdentity local,String displayName,
                           LiveChatService service,Runnable closeNetwork){
        this(local,displayName,service,closeNetwork,null);
    }
    public LiveChatAdapter(DeviceIdentity local,String displayName,
                           LiveChatService service,Runnable closeNetwork,Runnable connectionSettings){
        this.connectionSettings=connectionSettings;
        this.local=Objects.requireNonNull(local);
        this.self=new ChatUser(local.deviceId().toString(),Objects.requireNonNull(displayName),true);
        this.service=Objects.requireNonNull(service);
        this.closeNetwork=Objects.requireNonNull(closeNetwork);
        service.addListener(this::onEvent);
    }
    public ChatUser self(){return self;}
    @Override public boolean liveMode(){return true;}
    @Override public boolean connectionSettingsAvailable(){return connectionSettings!=null;}
    @Override public void openConnectionSettings(){if(connectionSettings!=null)connectionSettings.run();}
    @Override public CompletionStage<ChatUser> signIn(String username,char[] password){
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "Live uses device fingerprint pairing, not demo username/password"));
    }
    @Override public CompletionStage<ChatUser> register(String username,char[] password){
        return signIn(username,password);
    }
    @Override public void connect(ChatClientListener listener){
        subscriber=Objects.requireNonNull(listener);
        Runnable initial=()->{
            if(!closed&&subscriber==listener)listener.onDirectoryChanged(snapshot());
        };
        if(javax.swing.SwingUtilities.isEventDispatchThread())initial.run();
        else javax.swing.SwingUtilities.invokeLater(initial);
    }
    private ChatSnapshot snapshot() {
        Map<UUID,String> peers=service.connectedPeers();
        List<ChatUser> users=new ArrayList<>();
        List<Conversation> conversations=new ArrayList<>();
        Map<String,ChatMessage> previews=new HashMap<>();
        synchronized(recentVerified){
            for(var e:peers.entrySet()){
                UUID conversation=service.conversationId(e.getKey());
                if(conversation==null)continue;
                recentVerified.remove(e.getKey()); // refresh recent alias/verified session ID
                recentVerified.put(e.getKey(),new PeerLabel(conversation,e.getValue()));
            }
            while(recentVerified.size()>MAX_RECENT_PEERS)
                recentVerified.remove(recentVerified.keySet().iterator().next());
            for(var e:recentVerified.entrySet()){
                UUID remote=e.getKey();
                PeerLabel label=e.getValue();
                boolean online=peers.containsKey(remote);
                String remoteId=remote.toString(),conversationId=label.conversationId().toString();
                // A mutable alias is never used to key or route a direct P2P session.
                users.add(new ChatUser(remoteId,label.alias(),online));
                conversations.add(new Conversation(conversationId,label.alias(),
                    false,List.of(self.id(),remoteId)));
                if(online){
                    List<LiveChatService.Message> messages=service.transcript(remote);
                    if(!messages.isEmpty())
                        previews.put(conversationId,map(messages.get(messages.size()-1)));
                }
            }
        }
        return new ChatSnapshot(users,conversations,previews);
    }
    @Override public CompletionStage<List<ChatMessage>> loadHistory(String conversationId){
        // Existing FE port name; strictly returns current process RAM, never durable History.
        try{
            UUID id=UUID.fromString(conversationId);
            for(var peer:service.connectedPeers().keySet()){
                if(id.equals(service.conversationId(peer))){
                    return CompletableFuture.completedFuture(service.transcript(peer).stream()
                        .map(this::map).toList());
                }
            }
            synchronized(recentVerified){
                if(recentVerified.values().stream().anyMatch(label->id.equals(label.conversationId())))
                    return CompletableFuture.completedFuture(List.of());
            }
            return CompletableFuture.failedFuture(new IllegalArgumentException("No verified conversation"));
        }catch(IllegalArgumentException failure){return CompletableFuture.failedFuture(failure);}
    }
    @Override public CompletionStage<ChatMessage> sendMessage(String conversationId,MessageContent content){
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Live closed"));
        if(content.type()!=MessageType.TEXT)
            return CompletableFuture.failedFuture(new UnsupportedOperationException("M2 only supports text/emoji"));
        UUID requested;
        try{requested=UUID.fromString(conversationId);}
        catch(IllegalArgumentException malformed){return CompletableFuture.failedFuture(malformed);}
        for(var remote:service.connectedPeers().keySet()){
            if(requested.equals(service.conversationId(remote)))
                return service.sendText(remote,content.text()).thenApply(this::map);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("Unverified/disconnected peer"));
    }
    @Override public CompletionStage<ChatMessage> retryMessage(String conversationId,String messageId){
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Live closed"));
        try{
            UUID requested=UUID.fromString(conversationId);
            UUID original=UUID.fromString(messageId);
            for(var remote:service.connectedPeers().keySet()){
                if(requested.equals(service.conversationId(remote)))
                    return service.retryUncertain(remote,original).thenApply(this::map);
            }
            return CompletableFuture.failedFuture(
                new IllegalStateException("Only current verified peer may be manually retried"));
        }catch(IllegalArgumentException malformed){
            return CompletableFuture.failedFuture(malformed);
        }
    }
    @Override public CompletionStage<Conversation> createGroup(String name,List<String> memberIds){
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Group chat not available in M2"));
    }
    @Override public void onMessageVisible(String conversationId,String messageId){
        try{
            UUID conversation=UUID.fromString(conversationId),message=UUID.fromString(messageId);
            for(var remote:service.connectedPeers().keySet()){
                if(conversation.equals(service.conversationId(remote))){
                    service.markViewed(remote,message,conversation);return;
                }
            }
        }catch(IllegalArgumentException malformed){/* no fake read receipt */}
    }
    private ChatMessage map(LiveChatService.Message message){
        MessageAttempt.Status status=service.status(message.messageId());
        DeliveryState shown=switch(status==null?MessageAttempt.Status.LOCAL_SENT:status){
            case READ -> DeliveryState.SEEN;
            case APP_ACKNOWLEDGED -> DeliveryState.DELIVERED;
            case SENDING, DRAFT -> DeliveryState.SENDING;
            case UNCERTAIN -> DeliveryState.UNCERTAIN;
            case FAILED -> DeliveryState.FAILED;
            case LOCAL_SENT -> DeliveryState.SENT;
        };
        UUID verifiedRemote=message.senderId().equals(local.deviceId())
            ?message.destinationId():message.senderId();
        var presented=ChatViewMapper.present(message,local.deviceId(),verifiedRemote,
            service.connectedPeers().getOrDefault(verifiedRemote,verifiedRemote.toString()),
            java.time.ZoneId.systemDefault(),java.util.Locale.getDefault());
        return new ChatMessage(presented.messageId().toString(),
            presented.conversationId().toString(),presented.verifiedSenderId().toString(),
            presented.utcInstant(),MessageContent.text(presented.text()),shown);
    }
    private void onEvent(LiveChatService.Event event) {
        if(closed)return;
        if(!javax.swing.SwingUtilities.isEventDispatchThread()){
            javax.swing.SwingUtilities.invokeLater(()->onEvent(event));
            return;
        }
        ChatClientListener current=subscriber;
        if(current==null)return;
        switch(event.kind()){
            case CONNECTED -> current.onDirectoryChanged(snapshot());
            case RECEIVED -> {
                current.onDirectoryChanged(snapshot());
                current.onMessageReceived(map(event.message()));
            }
            case SENT -> current.onMessageReceived(map(event.message()));
            case STATUS_CHANGED -> {
                MessageAttempt.Status status=event.status();
                if(status==MessageAttempt.Status.APP_ACKNOWLEDGED||status==MessageAttempt.Status.READ){
                    current.onDeliveryStateChanged(event.message().conversationId().toString(),
                        event.message().messageId().toString(),
                        status==MessageAttempt.Status.READ?DeliveryState.SEEN:DeliveryState.DELIVERED);
                } else if(status==MessageAttempt.Status.UNCERTAIN){
                    current.onDeliveryStateChanged(event.message().conversationId().toString(),
                        event.message().messageId().toString(),DeliveryState.UNCERTAIN);
                    current.onConnectionError("Chưa rõ tin đã đến. Chọn Gửi lại thủ công trên tin nhắn, có nguy cơ trùng tin.");
                }
            }
            case FAILED -> {
                if(event.message()!=null&&event.status()!=null){
                    DeliveryState failure=event.status()==MessageAttempt.Status.UNCERTAIN
                        ? DeliveryState.UNCERTAIN:DeliveryState.FAILED;
                    current.onDeliveryStateChanged(event.message().conversationId().toString(),
                        event.message().messageId().toString(),failure);
                }
                current.onDirectoryChanged(snapshot());
                current.onConnectionError("Gửi thất bại hoặc chưa rõ kết quả; không tự gửi lại.");
            }
            case DISCONNECTED -> {
                current.onDirectoryChanged(snapshot());
                current.onConnectionError(
                    "Kênh Chat Live đã ngắt hoặc thất bại; không tự kết nối lại.");
            }
        }
    }
    @Override public void close(){
        if(closed)return;
        closed=true;
        synchronized(recentVerified){recentVerified.clear();}
        service.close();
        closeNetwork.run();
        subscriber=null;
    }
}