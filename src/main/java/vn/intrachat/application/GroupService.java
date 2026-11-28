package vn.intrachat.application;

import vn.intrachat.domain.ChatGroup;
import vn.intrachat.domain.ChatText;
import vn.intrachat.protocol.WireMessage;
import vn.intrachat.persistence.HistoryRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owner-relayed, RAM-only groups over the existing LiveChatService TCP sessions. */
public final class GroupService implements AutoCloseable {
    public record Message(UUID messageId,UUID groupId,UUID senderId,Instant sentAt,String text) {}
    public record Event(ChatGroup group,Message message,UUID messageId,int received,int expected,String notice) {}
    public interface Invitation { CompletionStage<Boolean> decide(UUID ownerId,String ownerName,String groupName); }
    private record Pending(UUID ownerId,String name) {}
    private final LiveChatService tcp;
    private final GroupMedia media;
    public record MediaEvent(FileTransferService.Transfer transfer,int received,int expected,int failed) {}
    private final HistoryRepository history;
    private final Set<UUID> verified=ConcurrentHashMap.newKeySet();
    private final UUID me;
    private final Invitation invitation;
    private final Map<UUID,ChatGroup> groups=new ConcurrentHashMap<>();
    private final Map<UUID,Pending> pending=new ConcurrentHashMap<>();
    private final Map<UUID,String> memberNames=new ConcurrentHashMap<>();
    private final Map<UUID,Map<UUID,Boolean>> invitations=new ConcurrentHashMap<>();
    private final Map<UUID,CompletableFuture<ChatGroup>> creations=new ConcurrentHashMap<>();
    private final Map<UUID,LinkedHashMap<UUID,Message>> timelines=new ConcurrentHashMap<>();
    private final Map<UUID,Set<UUID>> receipts=new ConcurrentHashMap<>();
    private final Map<UUID,Set<UUID>> expected=new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<UUID> receiptOrder=new ConcurrentLinkedQueue<>();
    private final CopyOnWriteArrayList<Consumer<Event>> listeners=new CopyOnWriteArrayList<>();
    private final Duration inviteTimeout;
    private volatile boolean closed;

    public GroupService(LiveChatService tcp,Invitation invitation) {
        this(tcp,invitation,Duration.ofSeconds(30));
    }
    public GroupService(LiveChatService tcp,Invitation invitation,Duration inviteTimeout) {
        if(inviteTimeout==null||inviteTimeout.isZero()||inviteTimeout.isNegative())
            throw new IllegalArgumentException("Invalid invite timeout");
        this.inviteTimeout=inviteTimeout;
        this.tcp=Objects.requireNonNull(tcp);
        history=tcp.history();
        for(ChatGroup archive:history.groups())groups.put(archive.groupId(),archive);
        this.me=tcp.localId();
        for(ChatGroup g:groups.values())if(g.ownerId().equals(me)&&g.contains(me))verified.add(g.groupId());
        memberNames.put(me,tcp.localName());
        this.invitation=Objects.requireNonNull(invitation);
        media=new GroupMedia(tcp,groups::get,verified::contains);
        tcp.setGroupLookup(groups::get);
        tcp.onGroupFrame(this::receive);
        tcp.addListener(event -> {
            if(event.kind()==LiveChatService.EventKind.CONNECTED)restoreWith(event.peerId());
            if(event.kind()==LiveChatService.EventKind.DISCONNECTED){
                groups.values().stream().filter(g->!g.ownerId().equals(me)&&g.ownerId().equals(event.peerId()))
                    .forEach(g->verified.remove(g.groupId()));
                announce(null,null,null,0,0,"Thành viên mất kết nối: "+event.peerId());
            }
        });
    }
    public boolean ready(UUID id){
        ChatGroup g=groups.get(id);
        return g!=null&&verified.contains(id)&&g.contains(me)&&
            (g.ownerId().equals(me)||tcp.connectedPeers().containsKey(g.ownerId()));
    }
    private void restoreWith(UUID peer){
        for(ChatGroup g:groups.values()){
            if(g.ownerId().equals(me)&&g.contains(peer))sendRoster(g,peer);
            else if(g.ownerId().equals(peer)&&g.contains(me))
                send(peer,WireMessage.Type.GROUP_SYNC,g.groupId(),Map.of()).exceptionally(error->null);
        }
    }
    public void addListener(Consumer<Event> listener){listeners.add(listener);}
    public void addMediaListener(Consumer<MediaEvent> listener){
        media.addListener(e->listener.accept(new MediaEvent(e.transfer(),e.received(),e.expected(),e.failed())));
    }
    public FileTransferService.Transfer sendGroupMedia(UUID id,java.nio.file.Path source,model.MessageType type)throws IOException{
        return media.send(id,source,type);
    }
    public void cancelMedia(UUID id){media.cancel(id);}
    private void announce(ChatGroup group,Message message,UUID messageId,int count,int total,String notice){
        Event event=new Event(group,message,messageId,count,total,notice);
        for(var listener:listeners)try{listener.accept(event);}catch(RuntimeException ignored){}
    }
    public List<ChatGroup> groups(){return List.copyOf(groups.values());}
    public ChatGroup group(UUID id){return groups.get(id);}
    public String memberName(UUID id){return memberNames.get(id);}
    public List<Message> history(UUID id) {
        LinkedHashMap<UUID,Message> timeline=timelines.get(id);
        if(timeline==null){
            List<Message> loaded=new ArrayList<>();
            for(var e:history.loadMessages(id,500,0))if(e.conversationType().equals("GROUP")&&e.messageType().equals("TEXT"))
                loaded.add(new Message(e.messageId(),id,e.senderId(),e.sentAtUtc(),e.text()));
            return loaded;
        }
        synchronized(timeline){
            Map<UUID,Message> merged=new LinkedHashMap<>();
            for(var e:history.loadMessages(id,500,0))if(e.conversationType().equals("GROUP")&&e.messageType().equals("TEXT"))
                merged.put(e.messageId(),new Message(e.messageId(),id,e.senderId(),e.sentAtUtc(),e.text()));
            merged.putAll(timeline);return merged.values().stream()
                .sorted(Comparator.comparing(Message::sentAt).thenComparing(m->m.messageId().toString())).toList();
        }
    }
    private boolean save(Message message){
        LinkedHashMap<UUID,Message> timeline=timelines.computeIfAbsent(message.groupId(),id->new LinkedHashMap<>());
        synchronized(timeline){
            Message existing=timeline.get(message.messageId());
            if(existing!=null){
                if(!existing.equals(message))throw new IllegalArgumentException("Conflicting group message ID");
                return false;
            }
            if(timeline.size()>=500)timeline.remove(timeline.keySet().iterator().next());
            timeline.put(message.messageId(),message);
            history.appendMessage(new HistoryRepository.Entry(message.messageId(),message.groupId(),"GROUP",
                message.senderId(),null,message.sentAt(),"TEXT",message.text(),
                message.senderId().equals(me)?"SENDING":"DELIVERED",null,"",0,"",""));
            return true;
        }
    }
    private static String roster(ChatGroup group){
        return group.memberIds().stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
    }
    private static List<UUID> parseRoster(String encoded){
        if(encoded==null||encoded.isBlank())throw new IllegalArgumentException("Missing roster");
        return Arrays.stream(encoded.split(",",-1)).map(UUID::fromString).toList();
    }
    private CompletableFuture<Void> send(UUID peer,WireMessage.Type type,UUID groupId,Map<String,String> data){
        return tcp.sendGroup(peer,type,groupId,data);
    }
    public CompletionStage<ChatGroup> createGroup(String name,List<UUID> targets){
        if(groups.size()>=16||name==null||name.isBlank()||name.length()>60 || targets==null ||targets.isEmpty()||targets.size()>4
            ||new HashSet<>(targets).size()!=targets.size()||targets.contains(me))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid group"));
        if(!tcp.connectedPeers().keySet().containsAll(targets))
            return CompletableFuture.failedFuture(new IOException("Invitee offline"));
        UUID id=UUID.randomUUID();
        ChatGroup group=new ChatGroup(id,name.strip(),me,List.of(me),0);
        groups.put(id,group);verified.add(id);history.saveGroup(group);
        CompletableFuture<ChatGroup> first=new CompletableFuture<>();
        creations.put(id,first);
        Map<UUID,Boolean> states=new ConcurrentHashMap<>();
        targets.forEach(peer->states.put(peer,false));
        invitations.put(id,states);
        for(UUID peer:targets){
            send(peer,WireMessage.Type.GROUP_INVITE,id,Map.of("name",group.groupName(),"owner",me.toString()))
                .exceptionally(failure->{announce(group,null,null,0,0,"Không thể mời "+peer);return null;});
        }
        announce(group,null,null,0,0,"Đang mời "+targets.size()+" thành viên");
        CompletableFuture.delayedExecutor(inviteTimeout.toMillis(),TimeUnit.MILLISECONDS)
            .execute(()->expireInvitations(id));
        return first;
    }
    private synchronized void expireInvitations(UUID id){
        if(closed)return;
        Map<UUID,Boolean> states=invitations.remove(id);
        if(states==null)return;
        List<UUID> unanswered=states.entrySet().stream().filter(e->!e.getValue())
            .map(Map.Entry::getKey).toList();
        ChatGroup group=groups.get(id);
        for(UUID peer:unanswered)
            send(peer,WireMessage.Type.GROUP_REMOVE,id,Map.of()).exceptionally(error->null);
        CompletableFuture<ChatGroup> waiting=creations.remove(id);
        if(group!=null&&group.memberIds().size()<2){
            groups.remove(id,group);
            if(waiting!=null)waiting.completeExceptionally(new IOException("Group invitation timed out"));
        }
        if(!unanswered.isEmpty())announce(group,null,null,0,0,
            "Hết thời gian chờ phản hồi của "+unanswered.size()+" lời mời");
    }
    private Map<String,String> rosterPayload(ChatGroup group){
        Map<String,String> data=new HashMap<>(Map.of(
            "name",group.groupName(),"owner",group.ownerId().toString(),
            "roster",roster(group),"revision",Long.toString(group.revision())));
        for(UUID member:group.memberIds()){
            String label=member.equals(me)?tcp.localName():tcp.connectedPeers().get(member);
            if(label!=null){memberNames.put(member,label);data.put("label."+member,label);}
        }
        return data;
    }
    private void sendRoster(ChatGroup group,UUID peer){
        send(peer,WireMessage.Type.GROUP_UPDATE,group.groupId(),rosterPayload(group)).exceptionally(error->null);
    }
    private void update(ChatGroup group){
        history.saveGroup(group);
        for(UUID id:group.memberIds())if(!id.equals(me))sendRoster(group,id);
        announce(group,null,null,0,0,null);
    }
    public CompletionStage<Message> sendGroupText(UUID groupId,String text){
        ChatGroup group=groups.get(groupId);
        if(group==null||!verified.contains(groupId)||!group.contains(me)||group.memberIds().size()<2)
            return CompletableFuture.failedFuture(new IOException("Group unavailable"));
        if(!group.ownerId().equals(me)&&!tcp.connectedPeers().containsKey(group.ownerId()))
            return CompletableFuture.failedFuture(new IOException("Chủ nhóm offline; không thể gửi"));
        Message msg=new Message(UUID.randomUUID(),groupId,me,Instant.now(),new ChatText(text).text());
        Set<UUID> destinations=new HashSet<>(group.memberIds());destinations.remove(me);
        expected.put(msg.messageId(),Set.copyOf(destinations));
        receipts.put(msg.messageId(),ConcurrentHashMap.newKeySet());
        receiptOrder.add(msg.messageId());
        while(receiptOrder.size()>512){
            UUID old=receiptOrder.poll();
            if(old!=null){expected.remove(old);receipts.remove(old);}
        }
        save(msg);
        announce(group,msg,msg.messageId(),0,destinations.size(),null);
        Map<String,String> body=body(msg);
        List<CompletableFuture<Void>> sends=new ArrayList<>();
        if(me.equals(group.ownerId())){
            for(UUID recipient:destinations)
                sends.add(send(recipient,WireMessage.Type.GROUP_TEXT,groupId,body));
        } else {
            sends.add(send(group.ownerId(),WireMessage.Type.GROUP_TEXT,groupId,body));
        }
        return CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new))
            .thenApply(ignored->msg).whenComplete((sent,error)->{
                if(error!=null)announce(group,null,msg.messageId(),
                    receipts.getOrDefault(msg.messageId(),Set.of()).size(),destinations.size(),
                    "Gửi nhóm thất bại/chưa rõ");
            });
    }
    private static Map<String,String> body(Message msg){
        return Map.of("originalMessageId",msg.messageId().toString(),"origin",msg.senderId().toString(),
            "sentAt",msg.sentAt().toString(),"text",msg.text());
    }
    public CompletionStage<Void> leave(UUID groupId){
        ChatGroup group=groups.get(groupId);
        if(group==null||!group.contains(me))return CompletableFuture.failedFuture(new IOException("Not in group"));
        if(me.equals(group.ownerId()))return CompletableFuture.failedFuture(new IOException("Owner cannot leave; remove members first"));
        return send(group.ownerId(),WireMessage.Type.GROUP_LEAVE,groupId,Map.of()).thenRun(()->{
            groups.remove(groupId);verified.remove(groupId);
            history.saveGroup(new ChatGroup(group.groupId(),group.groupName(),group.ownerId(),
                group.memberIds().stream().filter(id->!id.equals(me)).toList(),group.revision()+1));
            announce(group,null,null,0,0,"Đã rời nhóm");
        });
    }
    public CompletionStage<Void> remove(UUID groupId,UUID member){
        ChatGroup group=groups.get(groupId);
        if(group==null||!group.ownerId().equals(me)||me.equals(member)||!group.contains(member))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Only owner can remove member"));
        List<UUID> remaining=new ArrayList<>(group.memberIds());remaining.remove(member);
        ChatGroup changed=group.withMembers(remaining);
        groups.put(groupId,changed);
        update(changed);
        return send(member,WireMessage.Type.GROUP_REMOVE,groupId,Map.of())
            .exceptionally(failure->null);
    }
    private synchronized void receive(UUID via,WireMessage frame){
        if(closed)return;
        UUID id=frame.conversationId();
        Map<String,String> data=frame.payload();
        switch(frame.type()){
            case GROUP_INVITE -> {
                if(!via.toString().equals(data.get("owner"))||groups.containsKey(id))return;
                String name=data.get("name");
                if(name==null||name.isBlank()||name.length()>60)return;
                pending.put(id,new Pending(via,name));
                String label=tcp.connectedPeers().getOrDefault(via,via.toString());
                memberNames.put(via,label);
                invitation.decide(via,label,name).whenComplete((yes,error)->{
                    synchronized(GroupService.this){
                    Pending invite=pending.remove(id);
                    if(invite==null||closed)return;
                    boolean accepted=error==null&&Boolean.TRUE.equals(yes);
                    if(accepted){
                        ChatGroup joined=new ChatGroup(id,name,via,List.of(via,me),0);
                        groups.put(id,joined);verified.add(id);history.saveGroup(joined);
                        announce(joined,null,null,0,0,"Đã chấp nhận lời mời");
                    }
                    send(via,accepted?WireMessage.Type.GROUP_ACCEPT:WireMessage.Type.GROUP_REJECT,id,Map.of())
                        .exceptionally(failure->{announce(null,null,null,0,0,"Không thể phản hồi lời mời");return null;});
                    }
                });
            }
            case GROUP_ACCEPT -> {
                ChatGroup group=groups.get(id);
                if(group==null||!group.ownerId().equals(me))return;
                Map<UUID,Boolean> invites=invitations.get(id);
                if(invites==null||!invites.containsKey(via)||Boolean.TRUE.equals(invites.put(via,true)))return;
                if(group.memberIds().size()>=5)return;
                List<UUID> members=new ArrayList<>(group.memberIds());members.add(via);
                ChatGroup changed=group.withMembers(members);groups.put(id,changed);update(changed);
                CompletableFuture<ChatGroup> first=creations.remove(id);
                if(first!=null)first.complete(changed);
            }
            case GROUP_REJECT -> {
                Map<UUID,Boolean> invites=invitations.get(id);
                if(invites!=null&&invites.containsKey(via)){invites.remove(via);
                    announce(groups.get(id),null,null,0,0,"Lời mời bị từ chối: "+via);
                    if(invites.isEmpty()&&groups.get(id).memberIds().size()==1){
                        CompletableFuture<ChatGroup> first=creations.remove(id);
                        if(first!=null)first.completeExceptionally(new IOException("All invitations declined"));
                        groups.remove(id);
                    }
                }
            }
            case GROUP_UPDATE -> {
                UUID owner=UUID.fromString(data.get("owner"));
                ChatGroup old=groups.get(id);
                if(old==null||!old.ownerId().equals(via)||!via.equals(owner))return;
                long version=Long.parseLong(data.get("revision"));
                if(version<old.revision())return;
                ChatGroup changed=new ChatGroup(id,old.groupName(),owner,parseRoster(data.get("roster")),version);
                if(!changed.contains(me)){groups.remove(id);verified.remove(id);history.saveGroup(changed);
                    announce(old,null,null,0,0,"Bạn không còn trong nhóm");}
                else {
                    for(UUID member:changed.memberIds()){
                        String label=data.get("label."+member);
                        if(label!=null&&!label.isBlank()&&label.length()<=80)memberNames.put(member,label);
                    }
                    groups.put(id,changed);verified.add(id);history.saveGroup(changed);announce(changed,null,null,0,0,null);
                }
            }
            case GROUP_SYNC -> {
                ChatGroup group=groups.get(id);
                if(group!=null&&group.ownerId().equals(me)){
                    if(group.contains(via))sendRoster(group,via);
                    else send(via,WireMessage.Type.GROUP_REMOVE,id,Map.of()).exceptionally(error->null);
                }
            }
            case GROUP_TEXT -> receiveText(via,id,data);
            case GROUP_ACK -> receiveAck(via,id,data);
            case GROUP_MEDIA_STATUS -> media.receiveStatus(via,id,data);
            case GROUP_LEAVE -> {
                ChatGroup group=groups.get(id);
                if(group!=null&&group.ownerId().equals(me)&&group.contains(via)){
                    List<UUID> remaining=new ArrayList<>(group.memberIds());remaining.remove(via);
                    ChatGroup changed=group.withMembers(remaining);groups.put(id,changed);update(changed);
                }
            }
            case GROUP_REMOVE -> {
                ChatGroup group=groups.get(id);
                Pending invitation=pending.get(id);
                if(invitation!=null&&invitation.ownerId().equals(via)){
                    pending.remove(id);
                    announce(null,null,null,0,0,"Lời mời nhóm đã hết hạn hoặc bị hủy");
                }
                if(group!=null&&group.ownerId().equals(via)){
                    groups.remove(id);verified.remove(id);
                    history.saveGroup(new ChatGroup(group.groupId(),group.groupName(),group.ownerId(),
                        group.memberIds().stream().filter(member->!member.equals(me)).toList(),group.revision()+1));
                    announce(group,null,null,0,0,"Chủ nhóm đã loại bạn khỏi nhóm");
                }
            }
            default -> {}
        }
    }
    private void receiveText(UUID via,UUID id,Map<String,String> data){
        ChatGroup group=groups.get(id);
        if(group==null||!verified.contains(id)||!group.contains(me))return;
        UUID origin=UUID.fromString(data.get("origin"));
        UUID mid=UUID.fromString(data.get("originalMessageId"));
        if(!group.contains(origin)||origin.equals(me))return;
        boolean owner=me.equals(group.ownerId());
        if(owner ? !via.equals(origin) : !via.equals(group.ownerId()))return;
        Message msg=new Message(mid,id,origin,Instant.parse(data.get("sentAt")),new ChatText(data.get("text")).text());
        boolean fresh=save(msg);
        if(fresh)announce(group,msg,null,0,0,null);
        if(owner&&fresh){
            for(UUID recipient:group.memberIds()){
                if(recipient.equals(me)||recipient.equals(origin))continue;
                send(recipient,WireMessage.Type.GROUP_TEXT,id,body(msg)).exceptionally(err->null);
            }
        }
        send(via,WireMessage.Type.GROUP_ACK,id,Map.of("originalMessageId",mid.toString(),
            "recipient",me.toString(),"origin",origin.toString())).exceptionally(err->null);
    }
    private void receiveAck(UUID via,UUID id,Map<String,String> data){
        ChatGroup group=groups.get(id);if(group==null||!group.contains(me))return;
        UUID mid=UUID.fromString(data.get("originalMessageId"));
        UUID origin=UUID.fromString(data.get("origin"));
        UUID recipient=UUID.fromString(data.get("recipient"));
        if(me.equals(group.ownerId())){
            if(!via.equals(recipient)||!group.contains(recipient))return;
            if(!me.equals(origin)&&group.contains(origin))
                send(origin,WireMessage.Type.GROUP_ACK,id,data).exceptionally(err->null);
        } else if(!via.equals(group.ownerId()))return;
        if(!me.equals(origin))return;
        Set<UUID> wait=expected.get(mid),acked=receipts.get(mid);
        if(wait==null||acked==null||!wait.contains(recipient))return;
        if(acked.add(recipient)){
            if(acked.size()==wait.size())history.updateState(mid,"DELIVERED");
            announce(group,null,mid,acked.size(),wait.size(),null);
        }
    }
    @Override public void close(){
        media.close();closed=true;groups.clear();pending.clear();listeners.clear();
        expected.clear();receipts.clear();receiptOrder.clear();invitations.clear();timelines.clear();memberNames.clear();
        creations.values().forEach(f->f.completeExceptionally(new IOException("Closed")));
        creations.clear();
    }
}
