package listener;

import model.*;
import vn.intrachat.application.LiveChatService;
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
    private volatile ChatClientListener subscriber;
    private volatile boolean closed;

    public LiveChatAdapter(DeviceIdentity local,String displayName,
                           LiveChatService service,Runnable closeNetwork){
        this.local=Objects.requireNonNull(local);
        this.self=new ChatUser(local.deviceId().toString(),Objects.requireNonNull(displayName),true);
        this.service=Objects.requireNonNull(service);
        this.closeNetwork=Objects.requireNonNull(closeNetwork);
        service.addListener(this::onEvent);
    }
    public ChatUser self(){return self;}
    @Override public boolean liveMode(){return true;}
    @Override public CompletionStage<ChatUser> signIn(String username,char[] password){
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "Live uses device fingerprint pairing, not demo username/password"));
    }
    @Override public CompletionStage<ChatUser> register(String username,char[] password){
        return signIn(username,password);
    }
    @Override public void connect(ChatClientListener listener){
        subscriber=Objects.requireNonNull(listener);
        listener.onDirectoryChanged(snapshot());
    }
    private ChatSnapshot snapshot() {
        Map<UUID,String> peers=service.connectedPeers();
        List<ChatUser> users=new ArrayList<>();
        List<Conversation> conversations=new ArrayList<>();
        Map<String,ChatMessage> previews=new HashMap<>();
        for(var e:peers.entrySet()){
            String peerId=e.getKey().toString();
            UUID conversationId=service.conversationId(e.getKey());
            if(conversationId==null)continue;
            users.add(new ChatUser(peerId,e.getValue(),true));
            conversations.add(new Conversation(conversationId.toString(),e.getValue(),
                false,List.of(self.id(),peerId)));
            List<LiveChatService.Message> messages=service.transcript(e.getKey());
            if(!messages.isEmpty()){
                LiveChatService.Message latest=messages.get(messages.size()-1);
                previews.put(conversationId.toString(),map(latest));
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
            default -> DeliveryState.SENT;
        };
        return new ChatMessage(message.messageId().toString(),message.conversationId().toString(),
            message.senderId().toString(),message.sentAt(),
            MessageContent.text(message.text()),shown);
    }
    private void onEvent(LiveChatService.Event event) {
        if(closed)return;
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
                    current.onConnectionError("Chưa rõ tin đã đến. Bạn có thể thử lại thủ công, có nguy cơ trùng tin.");
                }
            }
            case DISCONNECTED, FAILED -> {
                current.onDirectoryChanged(snapshot());
                current.onConnectionError(
                    "Kênh Chat Live đã ngắt hoặc thất bại; không tự kết nối lại.");
            }
        }
    }
    @Override public void close(){
        if(closed)return;
        closed=true;
        service.close();
        closeNetwork.run();
        subscriber=null;
    }
}
