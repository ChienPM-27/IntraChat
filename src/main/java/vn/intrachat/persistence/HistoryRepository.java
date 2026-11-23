package vn.intrachat.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.intrachat.application.FileTransferService;
import vn.intrachat.domain.ChatGroup;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Per-profile append-only local history. One bounded writer serializes durable events.
 * A corrupt JSONL row is skipped, never used to overwrite or truncate valid rows.
 */
public final class HistoryRepository implements AutoCloseable {
    private static final Logger LOG=Logger.getLogger(HistoryRepository.class.getName());
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final int MAX_ROW=131072, MAX_ITEMS=10000, MAX_VIEW=500;
    private final Path file;
    private final ThreadPoolExecutor writer;
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<UUID,ChatGroup> groups=new HashMap<>();
    private final Map<UUID,String> peers=new HashMap<>();
    private volatile IOException writeFailure;
    private volatile boolean closed;

    public record Entry(UUID messageId,UUID conversationId,String conversationType,UUID senderId,
                        UUID recipientId,Instant sentAtUtc,String messageType,String text,
                        String deliveryState,UUID transferId,String fileName,long fileSize,
                        String transferState,String localPath) {}
    public HistoryRepository(Path dataDir) throws IOException {
        Path directory=dataDir.toAbsolutePath().normalize().resolve("history");
        Files.createDirectories(directory);
        file=directory.resolve("messages.jsonl");
        writer=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1024),r->{Thread t=new Thread(r,"intrachat-history-writer");t.setDaemon(true);return t;},
            (task,executor)->{throw new RejectedExecutionException("History writer queue full");});
        load();
    }
    public Path file(){return file;}
    public static boolean isFileType(String type){
        return Set.of("FILE","IMAGE","VIDEO","SOUND").contains(type);
    }
    private static UUID uuid(JsonNode n,String name){return UUID.fromString(n.path(name).asText());}
    private static String str(JsonNode n,String name){return n.path(name).asText("");}
    private synchronized void load() throws IOException {
        if(!Files.exists(file))return;
        try(BufferedReader input=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String line;long row=0;
            while((line=input.readLine())!=null){
                row++;
                if(line.length()>MAX_ROW){LOG.warning("Oversize history row "+row);continue;}
                try{
                    JsonNode n=JSON.readTree(line);
                    if(n==null)continue;
                    switch(str(n,"event")){
                        case "MESSAGE" -> {
                            UUID mid=uuid(n,"messageId"),cid=uuid(n,"conversationId"),sid=uuid(n,"senderId");
                            Instant time=Instant.parse(str(n,"sentAtUtc"));
                            String kind=str(n,"conversationType"),type=str(n,"messageType");
                            if(!Set.of("DIRECT","GROUP").contains(kind)||!(type.equals("TEXT")||isFileType(type)))
                                throw new IllegalArgumentException("Invalid message type");
                            String text=str(n,"text");
                            if(text.length()>32768)throw new IllegalArgumentException("Text too long");
                            UUID recipient=n.path("recipientId").isNull()||!n.hasNonNull("recipientId")?null:uuid(n,"recipientId");
                            UUID transfer=n.hasNonNull("transferId")?uuid(n,"transferId"):null;
                            Entry entry=new Entry(mid,cid,kind,sid,recipient,time,type,text,str(n,"deliveryState"),
                                transfer,str(n,"fileName"),n.path("fileSize").asLong(),str(n,"transferState"),str(n,"localPath"));
                            Entry existing=entries.get(mid);
                            if(existing==null){entries.put(mid,entry);trim();}
                            else if(!existing.conversationId().equals(cid)||!existing.senderId().equals(sid))
                                LOG.warning("Conflicting history message ID at row "+row);
                        }
                        case "STATE" -> {
                            Entry e=entries.get(uuid(n,"messageId"));
                            if(e!=null&&allowed(e.deliveryState(),str(n,"deliveryState")))
                                entries.put(e.messageId(),withState(e,str(n,"deliveryState"),e.transferState(),e.localPath()));
                        }
                        case "TRANSFER_STATE" -> {
                            Entry e=entries.get(uuid(n,"messageId"));
                            String nextTransfer=str(n,"transferState");
                            if(e!=null&&isFileType(e.messageType())
                                &&allowed(e.deliveryState(),str(n,"deliveryState"))
                                &&allowedTransfer(e.transferState(),nextTransfer))
                                entries.put(e.messageId(),withState(e,str(n,"deliveryState"),nextTransfer,str(n,"localPath")));
                        }
                        case "GROUP" -> {
                            UUID gid=uuid(n,"groupId"),owner=uuid(n,"ownerId");
                            List<UUID> members=new ArrayList<>();
                            for(JsonNode member:n.path("members"))members.add(UUID.fromString(member.asText()));
                            groups.put(gid,new ChatGroup(gid,str(n,"name"),owner,members,n.path("revision").asLong()));
                        }
                        case "PEER" -> {
                            UUID id=uuid(n,"peerId");String alias=str(n,"alias");
                            if(!alias.isBlank()&&alias.length()<=80)peers.put(id,alias);
                        }
                        default -> LOG.warning("Unknown history event at row "+row);
                    }
                }catch(Exception broken){LOG.log(Level.WARNING,"Skipping malformed history row "+row,broken);}
            }
        }
        // Running transfers cannot resume after process restart.
        entries.replaceAll((id,e)->isFileType(e.messageType())&&!Set.of("COMPLETED","REJECTED","CANCELLED","FAILED").contains(e.transferState())
            ?withState(e,"FAILED","INTERRUPTED",e.localPath()):e);
    }
    private void trim(){while(entries.size()>MAX_ITEMS)entries.remove(entries.keySet().iterator().next());}
    private static boolean allowed(String current,String next){
        if(current.equals(next))return true;
        if(current.equals("READ")||current.equals("SEEN")||current.equals("FAILED"))return false;
        if(current.equals("APP_ACKNOWLEDGED")||current.equals("DELIVERED"))
            return next.equals("READ")||next.equals("SEEN");
        if(current.equals("LOCAL_SENT")||current.equals("SENT"))
            return Set.of("APP_ACKNOWLEDGED","DELIVERED","READ","SEEN","UNCERTAIN","FAILED").contains(next);
        if(current.equals("UNCERTAIN"))
            return Set.of("APP_ACKNOWLEDGED","DELIVERED","READ","SEEN","FAILED").contains(next);
        return Set.of("LOCAL_SENT","SENT","UNCERTAIN","FAILED","APP_ACKNOWLEDGED","DELIVERED","READ","SEEN","SENDING").contains(next);
    }
    private static boolean allowedTransfer(String current,String next){
        if(current.equals(next))return true;
        if(Set.of("COMPLETED","REJECTED","CANCELLED","FAILED","INTERRUPTED").contains(current))return false;
        return Set.of("OFFERED","WAITING_ACCEPT","TRANSFERRING","COMPLETED",
            "REJECTED","CANCELLED","FAILED","INTERRUPTED").contains(next);
    }
    private static Entry withState(Entry e,String state,String transfer,String path){
        return new Entry(e.messageId(),e.conversationId(),e.conversationType(),e.senderId(),e.recipientId(),
            e.sentAtUtc(),e.messageType(),e.text(),state,e.transferId(),e.fileName(),e.fileSize(),transfer,path);
    }
    private void queue(Map<String,Object> event){
        if(closed)throw new IllegalStateException("History closed");
        if(writeFailure!=null)throw new IllegalStateException("History writer failed",writeFailure);
        try{
            byte[] bytes=(JSON.writeValueAsString(event)+"\n").getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_ROW)throw new IllegalArgumentException("History row too large");
            writer.execute(()->{
                try(FileChannel channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE)){
                    long size=channel.size();
                    if(size>0){
                        ByteBuffer tail=ByteBuffer.allocate(1);channel.read(tail,size-1);tail.flip();
                        if(tail.get()!=10){channel.position(size);channel.write(ByteBuffer.wrap(new byte[]{10}));}
                    }
                    channel.position(channel.size());
                    ByteBuffer buffer=ByteBuffer.wrap(bytes);
                    while(buffer.hasRemaining())channel.write(buffer);
                    channel.force(false);
                }catch(IOException problem){writeFailure=problem;LOG.log(Level.SEVERE,"History write failed",problem);}
            });
        }catch(IOException | RejectedExecutionException ex){throw new IllegalStateException("Cannot queue history record",ex);}
    }
    private static Map<String,Object> event(String kind){Map<String,Object> e=new LinkedHashMap<>();e.put("event",kind);return e;}
    public synchronized boolean appendMessage(Entry entry){
        Objects.requireNonNull(entry);
        Entry old=entries.get(entry.messageId());
        if(old!=null){
            if(!old.conversationId().equals(entry.conversationId())||!old.senderId().equals(entry.senderId()))
                throw new IllegalArgumentException("Conflicting history ID");
            return false;
        }
        Map<String,Object> data=event("MESSAGE");
        data.put("messageId",entry.messageId());data.put("conversationId",entry.conversationId());
        data.put("conversationType",entry.conversationType());data.put("senderId",entry.senderId());
        data.put("recipientId",entry.recipientId());data.put("sentAtUtc",entry.sentAtUtc().toString());
        data.put("messageType",entry.messageType());data.put("text",entry.text());
        data.put("deliveryState",entry.deliveryState());data.put("transferId",entry.transferId());
        data.put("fileName",entry.fileName());data.put("fileSize",entry.fileSize());
        data.put("transferState",entry.transferState());data.put("localPath",entry.localPath());
        queue(data);entries.put(entry.messageId(),entry);trim();return true;
    }
    public synchronized void updateState(UUID id,String state){
        Entry old=entries.get(id);if(old==null||old.deliveryState().equals(state)||!allowed(old.deliveryState(),state))return;
        Map<String,Object> data=event("STATE");data.put("messageId",id);data.put("deliveryState",state);
        queue(data);entries.put(id,withState(old,state,old.transferState(),old.localPath()));
    }
    public synchronized void updateTransfer(UUID id,String state,String transfer,String path){
        Entry old=entries.get(id);
        if(old==null||!isFileType(old.messageType())||!allowed(old.deliveryState(),state)
            ||!allowedTransfer(old.transferState(),transfer))return;
        if(old.transferState().equals(transfer)&&old.localPath().equals(path))return;
        Map<String,Object> data=event("TRANSFER_STATE");
        data.put("messageId",id);data.put("deliveryState",state);data.put("transferState",transfer);data.put("localPath",path);
        queue(data);entries.put(id,withState(old,state,transfer,path));
    }
    public synchronized void saveGroup(ChatGroup group){
        Map<String,Object> data=event("GROUP");data.put("groupId",group.groupId());
        data.put("name",group.groupName());data.put("ownerId",group.ownerId());
        data.put("members",group.memberIds());data.put("revision",group.revision());
        queue(data);groups.put(group.groupId(),group);
    }
    public synchronized void savePeer(UUID id,String alias){
        if(alias==null||alias.isBlank()||alias.length()>80||alias.equals(peers.get(id)))return;
        Map<String,Object> data=event("PEER");data.put("peerId",id);data.put("alias",alias);
        queue(data);peers.put(id,alias);
    }
    public synchronized Map<UUID,String> peers(){return Map.copyOf(peers);}
    public synchronized List<ChatGroup> groups(){return List.copyOf(groups.values());}
    public synchronized Entry find(UUID id){return entries.get(id);}
    public synchronized List<Entry> loadMessages(UUID conversation,int limit,int offset){
        int cap=Math.max(0,Math.min(limit,MAX_VIEW));
        List<Entry> found=new ArrayList<>();
        for(Entry e:entries.values())if(e.conversationId().equals(conversation))found.add(e);
        found.sort(Comparator.comparing(Entry::sentAtUtc).thenComparing(e->e.messageId().toString()));
        int end=Math.max(0,found.size()-Math.max(0,offset));
        return List.copyOf(found.subList(Math.max(0,end-cap),end));
    }
    public synchronized Set<UUID> conversations(){
        Set<UUID> ids=new LinkedHashSet<>();
        for(Entry e:entries.values())ids.add(e.conversationId());
        ids.addAll(groups.keySet());return ids;
    }
    public void flush() throws IOException {
        try{writer.submit(()->{}).get(15,TimeUnit.SECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        catch(ExecutionException|TimeoutException|RejectedExecutionException e){throw new IOException(e);}
        if(writeFailure!=null)throw writeFailure;
    }
    @Override public void close(){
        if(closed)return;
        try{flush();}catch(IOException ex){LOG.log(Level.SEVERE,"History flush failed",ex);}
        closed=true;writer.shutdown();
        try{if(!writer.awaitTermination(15,TimeUnit.SECONDS))writer.shutdownNow();}
        catch(InterruptedException ex){Thread.currentThread().interrupt();writer.shutdownNow();}
    }
}
