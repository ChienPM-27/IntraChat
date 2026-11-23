package listener;

import model.*;
import vn.intrachat.application.LiveChatService;
import vn.intrachat.application.ChatViewMapper;
import vn.intrachat.domain.MessageAttempt;
import vn.intrachat.application.LocalProfile;
import vn.intrachat.application.GroupService;
import vn.intrachat.application.FileTransferService;
import java.nio.file.Path;
import java.io.IOException;
import vn.intrachat.domain.ChatGroup;
import vn.intrachat.persistence.HistoryRepository;

import java.util.*;
import java.util.concurrent.*;

/**
 * Messenger FE Live adapter backed by plain TCP socket events and RAM-only transcripts.
 * No demo login, seeded conversations, artificial delivery/read timers or persisted History.
 */
public final class LiveChatAdapter implements ChatActionHandler {
    private final LocalProfile local;
    private final LiveChatService service;
    private final ChatUser self;
    private final GroupService groups;
    private final HistoryRepository history;
    private final Map<UUID,FileTransferService.Transfer> files=new ConcurrentHashMap<>();
    private final Map<UUID,DeliveryState> groupStates=new ConcurrentHashMap<>();
    private final Map<UUID,String> groupReceiptLabels=new ConcurrentHashMap<>();
    private final Runnable closeNetwork;
    private final Runnable connectionSettings;
    // In-process only: known peer IDs/aliases for offline RAM drafts. No chat
    // bodies, keys, receipts or persistent contact/History storage.
    private final LinkedHashMap<UUID,PeerLabel> recentPeers=new LinkedHashMap<>();
    private static final int MAX_RECENT_PEERS=32;
    private record PeerLabel(UUID conversationId,String alias){}
    private volatile ChatClientListener subscriber;
    private volatile boolean closed;

    public LiveChatAdapter(LocalProfile local,String displayName,
                           LiveChatService service,Runnable closeNetwork){
        this(local,displayName,service,closeNetwork,null);
    }
    public LiveChatAdapter(LocalProfile local,String displayName,
                           LiveChatService service,Runnable closeNetwork,Runnable connectionSettings){
        this.connectionSettings=connectionSettings;
        this.local=Objects.requireNonNull(local);
        this.self=new ChatUser(local.deviceId().toString(),Objects.requireNonNull(displayName),true);
        this.service=Objects.requireNonNull(service);
        this.history=service.history();
        for(var e:history.peers().entrySet())recentPeers.put(e.getKey(),
            new PeerLabel(service.conversationId(e.getKey()),e.getValue()));
        for(UUID id:history.conversations()){
            for(var row:history.loadMessages(id,500,0)){
                if(HistoryRepository.isFileType(row.messageType())){
                    try{
                        FileTransferService.State state=Set.of("INTERRUPTED","TRANSFERRING","WAITING_ACCEPT","OFFERED").contains(row.transferState())
                            ?FileTransferService.State.FAILED:FileTransferService.State.valueOf(row.transferState());
                        Path path=row.localPath().isBlank()?null:Path.of(row.localPath());
                        files.put(row.messageId(),new FileTransferService.Transfer(row.messageId(),row.conversationId(),
                            row.senderId(),row.recipientId(),row.fileName(),row.fileSize(),
                            state==FileTransferService.State.COMPLETED?row.fileSize():0,state,path,
                            row.transferState().equals("INTERRUPTED")?"Gián đoạn do khởi động lại":null,row.sentAtUtc(),
                             MessageType.valueOf(row.messageType())));
                    }catch(RuntimeException malformed){java.util.logging.Logger.getLogger(getClass().getName()).warning("Invalid persisted file metadata: "+row.messageId());}
                }
            }
        }
        this.closeNetwork=Objects.requireNonNull(closeNetwork);
        service.addListener(this::onEvent);
        service.addFileListener(this::onFileEvent);
        groups=new GroupService(service,(owner,label,name)->{
            CompletableFuture<Boolean> answer=new CompletableFuture<>();
            javax.swing.SwingUtilities.invokeLater(()->{
                int option=javax.swing.JOptionPane.showConfirmDialog(null,
                    label+" mời bạn tham gia nhóm: "+name+". Chấp nhận?",
                    "Lời mời nhóm IntraChat",javax.swing.JOptionPane.YES_NO_OPTION);
                answer.complete(option==javax.swing.JOptionPane.YES_OPTION);
            });
            return answer;
        });
        groups.addListener(this::onGroupEvent);
    }
    public ChatUser self(){return self;}
    @Override public boolean liveMode(){return true;}
    @Override public boolean connectionSettingsAvailable(){return connectionSettings!=null;}
    @Override public void openConnectionSettings(){if(connectionSettings!=null)connectionSettings.run();}
    @Override public CompletionStage<ChatUser> signIn(String username,char[] password){
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "Live uses local device profiles, not demo username/password"));
    }
    @Override public CompletionStage<ChatUser> register(String username,char[] password){
        return signIn(username,password);
    }
    @Override public void connect(ChatClientListener listener){
        subscriber=Objects.requireNonNull(listener);
        Runnable initial=()->{
            if(!closed&&subscriber==listener){
                listener.onDirectoryChanged(snapshot());
                for(var file:files.values())listener.onFileTransfer(file);
            }
        };
        if(javax.swing.SwingUtilities.isEventDispatchThread())initial.run();
        else javax.swing.SwingUtilities.invokeLater(initial);
    }
    private ChatSnapshot snapshot() {
        Map<UUID,String> peers=service.connectedPeers();
        List<ChatUser> users=new ArrayList<>();
        List<Conversation> conversations=new ArrayList<>();
        Map<String,ChatMessage> previews=new HashMap<>();
        synchronized(recentPeers){
            for(var e:peers.entrySet()){
                UUID conversation=service.conversationId(e.getKey());
                if(conversation==null)continue;
                recentPeers.remove(e.getKey()); // refresh recent alias/session ID
                recentPeers.put(e.getKey(),new PeerLabel(conversation,e.getValue()));
            }
            while(recentPeers.size()>MAX_RECENT_PEERS)
                recentPeers.remove(recentPeers.keySet().iterator().next());
            for(var e:recentPeers.entrySet()){
                UUID remote=e.getKey();
                PeerLabel label=e.getValue();
                boolean online=peers.containsKey(remote);
                String remoteId=remote.toString(),conversationId=label.conversationId().toString();
                // A mutable alias is never used to key or route a direct P2P session.
                users.add(new ChatUser(remoteId,label.alias(),online));
                conversations.add(new Conversation(conversationId,label.alias(),
                    false,List.of(self.id(),remoteId)));
                List<LiveChatService.Message> messages=service.transcript(remote);
                if(!messages.isEmpty())
                    previews.put(conversationId,map(messages.get(messages.size()-1)));
            }
        }
        for(ChatGroup group:groups.groups()){
            if(group.memberIds().size()<2||!group.contains(local.deviceId()))continue;
            for(UUID member:group.memberIds()){
                if(member.equals(local.deviceId()))continue;
                String alias=peers.get(member);
                if(alias==null){synchronized(recentPeers){PeerLabel label=recentPeers.get(member);if(label!=null)alias=label.alias();}}
                if(alias==null)alias=groups.memberName(member);
                if(alias==null)alias=member.toString();
                String memberId=member.toString(),display=alias;
                if(users.stream().noneMatch(u->u.id().equals(memberId)))users.add(new ChatUser(memberId,display,peers.containsKey(member)));
            }
            String gid=group.groupId().toString();
            conversations.add(new Conversation(gid,group.groupName(),true,group.memberIds().stream().map(UUID::toString).toList()));
            List<GroupService.Message> groupMessages=groups.history(group.groupId());
            if(!groupMessages.isEmpty())previews.put(gid,mapGroup(groupMessages.get(groupMessages.size()-1)));
        }
        for(var transfer:files.values()){
            String id=transfer.conversationId().toString();
            ChatMessage candidate=fileMessage(transfer);
            ChatMessage prior=previews.get(id);
            if(prior==null||ChatMessage.ORDER.compare(prior,candidate)<0)previews.put(id,candidate);
        }
        return new ChatSnapshot(users,conversations,previews);
    }
    @Override public CompletionStage<List<ChatMessage>> loadHistory(String conversationId){
        // Disk-backed history is loaded on a worker, not Swing EDT.
        try{
            UUID id=UUID.fromString(conversationId);
            if(groups.group(id)!=null)
                return CompletableFuture.supplyAsync(()->{
                    Map<String,ChatMessage> merged=new LinkedHashMap<>();
                    for(var row:history.loadMessages(id,500,0))if(row.conversationType().equals("GROUP"))
                        merged.put(row.messageId().toString(),fromHistory(row));
                    for(var msg:groups.history(id))merged.put(msg.messageId().toString(),mapGroup(msg));
                    return merged.values().stream().sorted(ChatMessage.ORDER).toList();
                });
            for(var peer:service.connectedPeers().keySet()){
                if(id.equals(service.conversationId(peer))){
                    List<ChatMessage> all=new ArrayList<>(service.transcript(peer).stream().map(this::map).toList());
                    files.values().stream().filter(t->t.conversationId().equals(id)).map(this::fileMessage).forEach(all::add);
                    all.sort(ChatMessage.ORDER);
                    return CompletableFuture.supplyAsync(()->mergeHistory(id,all));
                }
            }
            synchronized(recentPeers){
                for(var entry:recentPeers.entrySet()){
                    if(id.equals(entry.getValue().conversationId())){
                        List<ChatMessage> all=new ArrayList<>(service.transcript(entry.getKey()).stream().map(this::map).toList());
                        files.values().stream().filter(t->t.conversationId().equals(id)).map(this::fileMessage).forEach(all::add);
                        all.sort(ChatMessage.ORDER);
                        return CompletableFuture.supplyAsync(()->mergeHistory(id,all));
                    }
                }
            }
            if(history.conversations().contains(id))return CompletableFuture.supplyAsync(()->mergeHistory(id,List.of()));
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown conversation"));
        }catch(IllegalArgumentException failure){return CompletableFuture.failedFuture(failure);}
    }
    @Override public CompletionStage<ChatMessage> sendMessage(String conversationId,MessageContent content){
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Live closed"));
        if(FileTransferService.MEDIA_TYPES.contains(content.type())){
            try{
                UUID conversation=UUID.fromString(conversationId);
                for(UUID peer:service.connectedPeers().keySet()){
                    if(conversation.equals(service.conversationId(peer))) {
                        UUID remote=peer;
                        return CompletableFuture.supplyAsync(()->{
                            try {return fileMessage(service.files().offer(remote,conversation,content.attachment().path(),content.type()));}
                            catch(IOException e){throw new CompletionException(e);}
                        });
                    }
                }
                return CompletableFuture.failedFuture(new IOException("Chỉ gửi file trong hội thoại P2P đang kết nối"));
            }catch(IllegalArgumentException e){return CompletableFuture.failedFuture(e);}
        }
        if(content.type()!=MessageType.TEXT)
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Media Live thuộc P5"));
        UUID requested;
        try{requested=UUID.fromString(conversationId);}
        catch(IllegalArgumentException malformed){return CompletableFuture.failedFuture(malformed);}
        if(groups.group(requested)!=null){
            return groups.sendGroupText(requested,content.text()).thenApply(message->{
                groupStates.compute(message.messageId(),(id,state)->state==DeliveryState.DELIVERED?state:DeliveryState.SENT);
                javax.swing.SwingUtilities.invokeLater(()->{
                    ChatClientListener current=subscriber;
                    if(current!=null)current.onDeliveryStateChanged(requested.toString(),message.messageId().toString(),
                        groupStates.get(message.messageId()));
                });
                return mapGroup(message);
            });
        }
        for(var remote:service.connectedPeers().keySet()){
            if(requested.equals(service.conversationId(remote)))
                return service.sendText(remote,content.text()).thenApply(this::map);
        }
        return CompletableFuture.failedFuture(new IllegalStateException("Disconnected peer"));
    }
    private List<ChatMessage> mergeHistory(UUID id,List<ChatMessage> current){
        Map<String,ChatMessage> byId=new LinkedHashMap<>();
        for(var row:history.loadMessages(id,500,0))byId.put(row.messageId().toString(),fromHistory(row));
        for(ChatMessage msg:current){
            if(!byId.containsKey(msg.id())||history.find(UUID.fromString(msg.id()))==null)byId.put(msg.id(),msg);
        }
        return byId.values().stream().sorted(ChatMessage.ORDER).toList();
    }
    private ChatMessage fromHistory(HistoryRepository.Entry row){
        if(HistoryRepository.isFileType(row.messageType())){
            var file=files.get(row.messageId());
            if(file!=null)return fileMessage(file);
        }
        DeliveryState state;
        try{state=DeliveryState.valueOf(row.deliveryState());}
        catch(IllegalArgumentException error){
            state=switch(row.deliveryState()){
                case "READ" -> DeliveryState.SEEN;
                case "APP_ACKNOWLEDGED" -> DeliveryState.DELIVERED;
                case "LOCAL_SENT" -> DeliveryState.SENT;
                case "UNCERTAIN" -> DeliveryState.UNCERTAIN;
                case "FAILED" -> DeliveryState.FAILED;
                default -> DeliveryState.SENDING;
            };
        }
        return new ChatMessage(row.messageId().toString(),row.conversationId().toString(),
            row.senderId().toString(),row.sentAtUtc(),MessageContent.text(row.text()),state);
    }
    private ChatMessage fileMessage(FileTransferService.Transfer t){
        Path path=t.localPath()==null?Path.of("incoming-file"):t.localPath();
        Attachment a=new Attachment(path,t.fileName(),t.fileSize(),0,0,0);
        DeliveryState state=switch(t.state()){
            case COMPLETED -> DeliveryState.DELIVERED;
            case REJECTED,CANCELLED,FAILED -> DeliveryState.FAILED;
            default -> DeliveryState.SENDING;
        };
        return new ChatMessage(t.transferId().toString(),t.conversationId().toString(),
            t.senderId().toString(),t.sentAt(),MessageContent.media(t.messageType(),a),state);
    }
    private void onFileEvent(FileTransferService.Transfer t){
        files.put(t.transferId(),t);
        javax.swing.SwingUtilities.invokeLater(()->{
            ChatClientListener current=subscriber;
            if(!closed&&current!=null){current.onFileTransfer(t);current.onMessageReceived(fileMessage(t));}
        });
    }
    @Override public void acceptFile(String id,Path destination)throws IOException{
        service.files().accept(UUID.fromString(id),destination);
    }
    @Override public void rejectFile(String id)throws IOException{
        service.files().reject(UUID.fromString(id));
    }
    @Override public void cancelFile(String id){service.files().cancel(UUID.fromString(id));}
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
                new IllegalStateException("Only current connected peer may be manually retried"));
        }catch(IllegalArgumentException malformed){
            return CompletableFuture.failedFuture(malformed);
        }
    }
    @Override public CompletionStage<Conversation> createGroup(String name,List<String> memberIds){
        try{
            return groups.createGroup(name,memberIds.stream().map(UUID::fromString).toList())
                .thenApply(g->new Conversation(g.groupId().toString(),g.groupName(),true,
                    g.memberIds().stream().map(UUID::toString).toList()));
        }catch(RuntimeException ex){return CompletableFuture.failedFuture(ex);}
    }
    @Override public boolean groupAvailable(){return true;}
    @Override public String groupReceiptLabel(String conversationId,String messageId){
        try{
            if(groups.group(UUID.fromString(conversationId))==null)return null;
            return groupReceiptLabels.get(UUID.fromString(messageId));
        }catch(IllegalArgumentException malformed){return null;}
    }
    @Override public CompletionStage<Void> leaveGroup(String id){
        try{return groups.leave(UUID.fromString(id));}catch(RuntimeException ex){return CompletableFuture.failedFuture(ex);}
    }
    @Override public CompletionStage<Void> removeGroupMember(String id,String member){
        try{return groups.remove(UUID.fromString(id),UUID.fromString(member));}
        catch(RuntimeException ex){return CompletableFuture.failedFuture(ex);}
    }
    @Override public boolean groupOwnerOnline(String id){
        try{ChatGroup g=groups.group(UUID.fromString(id));
            return g!=null&&(g.ownerId().equals(local.deviceId())||service.connectedPeers().containsKey(g.ownerId()));
        }catch(RuntimeException ex){return false;}
    }
    @Override public boolean isGroupOwner(String id){
        try{ChatGroup g=groups.group(UUID.fromString(id));return g!=null&&g.ownerId().equals(local.deviceId());}
        catch(RuntimeException ex){return false;}
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
    private ChatMessage mapGroup(GroupService.Message message){
        DeliveryState state=message.senderId().equals(local.deviceId())
            ?groupStates.getOrDefault(message.messageId(),DeliveryState.SENDING):DeliveryState.DELIVERED;
        var persisted=history.find(message.messageId());
        if(persisted!=null){try{state=fromHistory(persisted).state();}catch(RuntimeException ignored){}}
        return new ChatMessage(message.messageId().toString(),message.groupId().toString(),
            message.senderId().toString(),message.sentAt(),MessageContent.text(message.text()),state);
    }
    private void onGroupEvent(GroupService.Event event){
        if(closed)return;
        if(!javax.swing.SwingUtilities.isEventDispatchThread()){
            javax.swing.SwingUtilities.invokeLater(()->onGroupEvent(event));return;
        }
        ChatClientListener current=subscriber;
        if(current==null)return;
        if(event.message()!=null){
            if(event.message().senderId().equals(local.deviceId())){
                groupStates.putIfAbsent(event.message().messageId(),DeliveryState.SENDING);
                if(event.expected()>0)groupReceiptLabels.putIfAbsent(event.message().messageId(),
                    "Đã nhận 0/"+event.expected());
            }
            current.onDirectoryChanged(snapshot());
            current.onMessageReceived(mapGroup(event.message()));
        } else if(event.messageId()!=null){
            if(event.expected()>0&&event.group()!=null){
                groupReceiptLabels.put(event.messageId(),"Đã nhận "+event.received()+"/"+event.expected());
                current.onGroupReceiptChanged(event.group().groupId().toString(),event.messageId().toString());
            }
            if(event.notice()!=null&&event.notice().contains("thất bại")){
                groupStates.put(event.messageId(),DeliveryState.UNCERTAIN);
                if(event.group()!=null)current.onDeliveryStateChanged(event.group().groupId().toString(),
                    event.messageId().toString(),DeliveryState.UNCERTAIN);
            }else if(event.expected()>0&&event.received()==event.expected()){
                groupStates.put(event.messageId(),DeliveryState.DELIVERED);
                UUID id=event.group()==null?null:event.group().groupId();
                if(id!=null)current.onDeliveryStateChanged(id.toString(),event.messageId().toString(),DeliveryState.DELIVERED);
            } else if(event.expected()>0)current.onConnectionError("Nhóm đã nhận "+event.received()+"/"+event.expected());
        }
        if(event.group()!=null||event.notice()!=null)current.onDirectoryChanged(snapshot());
        if(event.notice()!=null)current.onConnectionError(event.notice());
    }
    private ChatMessage map(LiveChatService.Message message){
        MessageAttempt.Status status=service.status(message.messageId());
        var persisted=history.find(message.messageId());
        if(status==null&&persisted!=null)return fromHistory(persisted);
        DeliveryState shown=switch(status==null?MessageAttempt.Status.LOCAL_SENT:status){
            case READ -> DeliveryState.SEEN;
            case APP_ACKNOWLEDGED -> DeliveryState.DELIVERED;
            case SENDING, DRAFT -> DeliveryState.SENDING;
            case UNCERTAIN -> DeliveryState.UNCERTAIN;
            case FAILED -> DeliveryState.FAILED;
            case LOCAL_SENT -> DeliveryState.SENT;
        };
        UUID remoteId=message.senderId().equals(local.deviceId())
            ?message.destinationId():message.senderId();
        var presented=ChatViewMapper.present(message,local.deviceId(),remoteId,
            service.connectedPeers().getOrDefault(remoteId,remoteId.toString()),
            java.time.ZoneId.systemDefault(),java.util.Locale.getDefault());
        return new ChatMessage(presented.messageId().toString(),
            presented.conversationId().toString(),presented.senderId().toString(),
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
        synchronized(recentPeers){recentPeers.clear();}
        groups.close();
        service.close();
        closeNetwork.run();
        subscriber=null;
    }
}