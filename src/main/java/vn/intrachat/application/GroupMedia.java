package vn.intrachat.application;

import model.MessageType;
import vn.intrachat.domain.ChatGroup;
import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.protocol.WireMessage;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import static vn.intrachat.application.FileTransferService.*;

/** Coordinates logical group media using the existing bounded per-peer file transport.
 * The owner only forwards a locally published, CRC-verified file. */
final class GroupMedia implements AutoCloseable {
    record Event(Transfer transfer,int received,int expected,int failed) {}
    private final LiveChatService tcp;
    private final UUID me;
    private final Function<UUID,ChatGroup> group;
    private final Predicate<UUID> verified;
    private final HistoryRepository history;
    private final Map<UUID,Job> jobs=new LinkedHashMap<>();
    private final List<Consumer<Event>> listeners=new CopyOnWriteArrayList<>();
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"intrachat-group-media");t.setDaemon(true);return t;
    });
    private boolean closed;
    private static final int MAX_ACTIVE=16, MAX_TRACKED=256;
    private static final long DEADLINE_MS=10*60_000;

    private static final class Job {
        final GroupRef ref;
        final UUID groupId;
        final String name;
        final long size;
        final MessageType type;
        final boolean outgoing;
        final Set<UUID> expected;
        final Map<UUID,Boolean> outcomes=new HashMap<>();
        final Set<UUID> hops=new HashSet<>();
        final long deadline=System.currentTimeMillis()+DEADLINE_MS;
        Transfer local;
        boolean relaying;
        Job(GroupRef ref,UUID groupId,String name,long size,MessageType type,boolean outgoing,Set<UUID> expected){
            this.ref=ref;this.groupId=groupId;this.name=name;this.size=size;this.type=type;this.outgoing=outgoing;
            this.expected=Set.copyOf(expected);
        }
        boolean done(){return outgoing?outcomes.size()==expected.size():
            local!=null&&terminal(local.state())&&(!relaying||outcomes.size()==expected.size());}
    }
    GroupMedia(LiveChatService tcp,Function<UUID,ChatGroup> group,Predicate<UUID> verified){
        this.tcp=tcp;this.me=tcp.localId();this.group=group;this.verified=verified;this.history=tcp.history();
        tcp.onGroupMediaOffer(this::allowOffer);
        tcp.addFileListener(this::onTransfer);
        tcp.addListener(e->{if(e.kind()==LiveChatService.EventKind.DISCONNECTED)disconnected(e.peerId());});
        deadlines.scheduleAtFixedRate(this::expire,5,5,TimeUnit.SECONDS);
    }
    void addListener(Consumer<Event> listener){listeners.add(listener);}
    private static boolean terminal(State state){
        return state==State.COMPLETED||state==State.FAILED||state==State.CANCELLED||state==State.REJECTED;
    }
    private boolean available(UUID id){
        ChatGroup g=group.apply(id);
        return g!=null&&verified.test(id)&&g.contains(me)&&g.memberIds().size()>1;
    }
    private synchronized boolean allowOffer(UUID via,WireMessage frame){
        if(closed||!available(frame.conversationId()))return false;
        ChatGroup g=group.apply(frame.conversationId());
        GroupRef ref=GroupRef.parse(frame.payload());
        if(ref==null||ref.origin().equals(me)||!g.contains(ref.origin())||!g.contains(via))return false;
        if(g.ownerId().equals(me)?!via.equals(ref.origin()):!via.equals(g.ownerId()))return false;
        return jobs.get(ref.messageId())==null&&history.find(ref.messageId())==null&&room();
    }
    private boolean room(){
        while(jobs.size()>=MAX_TRACKED){
            UUID old=jobs.entrySet().stream().filter(e->e.getValue().done()).map(Map.Entry::getKey).findFirst().orElse(null);
            if(old==null)return false;
            jobs.remove(old);
        }
        return jobs.values().stream().filter(j->!j.done()).count()<MAX_ACTIVE;
    }
    private static Set<UUID> recipients(ChatGroup g,UUID origin){
        Set<UUID> targets=new HashSet<>(g.memberIds());targets.remove(origin);return targets;
    }
    Transfer send(UUID groupId,Path source,MessageType type)throws IOException{
        if(!MEDIA_TYPES.contains(type)||!Files.isRegularFile(source)||!Files.isReadable(source))throw new IOException("Tệp nguồn không hợp lệ");
        long size=Files.size(source);
        if(size>maxSize(type)||(size==0&&type!=MessageType.FILE))throw new IOException("Tệp vượt giới hạn hoặc rỗng");
        String name=safeFileName(source.getFileName().toString());
        Job job;
        ChatGroup g;
        synchronized(this){
            if(closed||!available(groupId)||!room())throw new IOException("Nhóm chưa sẵn sàng hoặc quá nhiều phiên media");
            g=group.apply(groupId);
            if(!g.ownerId().equals(me)&&!tcp.connectedPeers().containsKey(g.ownerId()))throw new IOException("Chủ nhóm offline");
            job=new Job(new GroupRef(UUID.randomUUID(),me,Instant.now()),groupId,name,size,type,true,recipients(g,me));
            job.local=logical(job,State.TRANSFERRING,source.toAbsolutePath().normalize(),0,null);
            jobs.put(job.ref.messageId(),job);
            publish(job,job.local);
        }
        if(g.ownerId().equals(me))fanOut(job,job.expected);
        else launch(job,g.ownerId(),true);
        return job.local;
    }
    private Transfer logical(Job j,State state,Path path,long bytes,String error){
        return new Transfer(j.ref.messageId(),j.groupId,j.ref.origin(),null,j.name,j.size,bytes,state,path,error,j.ref.sentAt(),j.type);
    }
    private void publish(Job j,Transfer t){
        String delivery=switch(t.state()){case COMPLETED -> "DELIVERED";case FAILED,CANCELLED,REJECTED -> "FAILED";default -> "SENDING";};
        String path=t.localPath()==null?"":t.localPath().toString();
        history.appendMessage(new HistoryRepository.Entry(t.transferId(),t.conversationId(),"GROUP",
            t.senderId(),null,t.sentAt(),t.messageType().name(),"",delivery,t.transferId(),t.fileName(),t.fileSize(),t.state().name(),path));
        history.updateTransfer(t.transferId(),delivery,t.state().name(),path);
        int count=(int)j.outcomes.values().stream().filter(Boolean::booleanValue).count();
        Event event=new Event(t,j.outgoing?count:0,j.outgoing?j.expected.size():0,j.outgoing?j.outcomes.size()-count:0);
        for(var listener:listeners)try{listener.accept(event);}catch(RuntimeException ignored){}
    }
    private void fanOut(Job j,Set<UUID> targets){for(UUID recipient:targets)launch(j,recipient,false);}
    private void launch(Job j,UUID target,boolean upload){
        workers.execute(()->{
            try{
                synchronized(this){
                    if(closed||j.outcomes.containsKey(target)||!available(j.groupId))throw new IOException("Nhóm không còn khả dụng");
                    ChatGroup g=group.apply(j.groupId);
                    if(!g.contains(target)||!tcp.connectedPeers().containsKey(target))throw new IOException("Thành viên offline hoặc đã rời nhóm");
                }
                tcp.files().offerGroup(target,j.groupId,j.local.localPath(),j.type,j.ref);
            }catch(Exception e){synchronized(this){
                if(upload)failOutstanding(j);
                else outcome(j,target,false);
            }}
        });
    }
    private synchronized void onTransfer(Transfer t){
        if(closed||t.groupRef()==null)return;
        GroupRef ref=t.groupRef();
        Job j=jobs.get(ref.messageId());
        if(me.equals(t.recipientId())){
            ChatGroup g=group.apply(t.conversationId());
            if(j==null){
                if(t.state()!=State.WAITING_ACCEPT||g==null||history.find(ref.messageId())!=null||!room()){
                    tcp.files().failReceive(t.transferId(),"Không thể nhận media nhóm");return;
                }
                Set<UUID> relay=g.ownerId().equals(me)?recipients(g,ref.origin()):Set.of();
                if(g.ownerId().equals(me))relay.remove(me);
                j=new Job(ref,t.conversationId(),t.fileName(),t.fileSize(),t.messageType(),false,relay);
                jobs.put(ref.messageId(),j);
            }else if(!j.hops.contains(t.transferId())){
                tcp.files().failReceive(t.transferId(),"Trùng tin media nhóm");return;
            }
            j.hops.add(t.transferId());
            j.local=logical(j,t.state(),t.localPath(),t.transferredBytes(),t.error());
            // Reserve relay before publishing completion, so room() cannot evict it.
            boolean relay=t.state()==State.COMPLETED&&g!=null&&g.ownerId().equals(me)&&!j.relaying;
            if(relay)j.relaying=true;
            publish(j,j.local);
            if(t.state()==State.WAITING_ACCEPT)tcp.acceptAutomatically(t);
            if(relay)fanOut(j,j.expected);
            return;
        }
        if(j==null||!me.equals(t.senderId()))return;
        j.hops.add(t.transferId());
        if(!terminal(t.state()))return;
        boolean success=t.state()==State.COMPLETED;
        ChatGroup g=group.apply(j.groupId);
        if(j.outgoing&&g!=null&&!g.ownerId().equals(me)&&!success)failOutstanding(j);
        else outcome(j,t.recipientId(),success);
    }
    private void failOutstanding(Job j){
        for(UUID target:j.expected)if(!j.outcomes.containsKey(target))outcome(j,target,false);
    }
    private void outcome(Job j,UUID target,boolean success){
        if(closed||!j.expected.contains(target)||j.outcomes.putIfAbsent(target,success)!=null)return;
        if(j.outgoing){
            int received=(int)j.outcomes.values().stream().filter(Boolean::booleanValue).count();
            boolean done=j.outcomes.size()==j.expected.size();
            State state=done?(received==j.expected.size()?State.COMPLETED:State.FAILED):State.TRANSFERRING;
            String error=done&&state==State.FAILED?"Đã nhận "+received+"/"+j.expected.size()+"; "+(j.expected.size()-received)+" thất bại":null;
            j.local=logical(j,state,j.local.localPath(),done&&state==State.COMPLETED?j.size:0,error);
            publish(j,j.local);
        }else{
            // Only the owner reports per-recipient outcomes to the original sender.
            tcp.sendGroup(j.ref.origin(),WireMessage.Type.GROUP_MEDIA_STATUS,j.groupId,Map.of(
                "originalMessageId",j.ref.messageId().toString(),"recipient",target.toString(),"success",Boolean.toString(success)))
                .exceptionally(e->null);
        }
    }
    synchronized void receiveStatus(UUID via,UUID id,Map<String,String> data){
        if(closed)return;
        ChatGroup g=group.apply(id);
        if(g==null||!g.ownerId().equals(via)||me.equals(via))return;
        Job j=jobs.get(UUID.fromString(data.get("originalMessageId")));
        if(j==null||!j.outgoing||!j.groupId.equals(id))return;
        UUID target=UUID.fromString(data.get("recipient"));
        if(target.equals(via))return; // owner's own result comes from FILE_RESULT.
        String success=data.get("success");
        if(!Set.of("true","false").contains(success))return;
        outcome(j,target,Boolean.parseBoolean(success));
    }
    synchronized void cancel(UUID id){
        Job j=jobs.get(id);if(j==null||j.done())return;
        List<UUID> hops=List.copyOf(j.hops);
        if(j.outgoing||j.relaying)failOutstanding(j);
        workers.execute(()->hops.forEach(tcp.files()::cancel));
    }
    private synchronized void disconnected(UUID peer){
        for(Job j:jobs.values()){
            if(j.done())continue;
            ChatGroup g=group.apply(j.groupId);
            if(j.outgoing&&g!=null&&g.ownerId().equals(peer))failOutstanding(j);
            else if(j.outgoing||j.relaying)outcome(j,peer,false);
        }
    }
    private synchronized void expire(){
        for(Job j:jobs.values())if(!j.done()&&System.currentTimeMillis()>j.deadline)cancel(j.ref.messageId());
    }
    @Override public synchronized void close(){
        if(closed)return;
        for(Job j:jobs.values())if(j.outgoing&&!j.done())failOutstanding(j);
        closed=true;deadlines.shutdownNow();workers.shutdownNow();listeners.clear();jobs.clear();
    }
}
