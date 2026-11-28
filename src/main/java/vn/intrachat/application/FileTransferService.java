package vn.intrachat.application;

import vn.intrachat.protocol.WireMessage;
import java.io.*;
import java.nio.file.*;
import model.MessageType;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.zip.CRC32;

/**
 * P3: single P2P file stream over existing bounded JSON/TCP LanSession.
 * One active file per peer, 32 KiB chunks, <=128 MiB, no whole-file buffering.
 * Completion requires receiver FILE_RESULT after flushed/validated file publication.
 */
public final class FileTransferService implements AutoCloseable {
    public static final int CHUNK_SIZE = 32 * 1024;
    public static final long MAX_FILE_SIZE = 128L * 1024 * 1024;
    public static final long ACCEPT_TIMEOUT_MS = 60_000;
    public static final long IDLE_TIMEOUT_MS = 60_000;
    public static final Set<MessageType> MEDIA_TYPES=Set.of(MessageType.FILE,MessageType.IMAGE,MessageType.VIDEO,MessageType.SOUND);
    public static long maxSize(MessageType type){
        return switch(type){case IMAGE -> 16L*1024*1024;case SOUND -> 8L*1024*1024;default -> MAX_FILE_SIZE;};
    }
    public enum State { OFFERED, WAITING_ACCEPT, TRANSFERRING, COMPLETED, REJECTED, CANCELLED, FAILED }
    /** Logical group identity is distinct from each hop's transfer ID. */
    public record GroupRef(UUID messageId, UUID origin, Instant sentAt) {
        public GroupRef { Objects.requireNonNull(messageId); Objects.requireNonNull(origin); Objects.requireNonNull(sentAt); }
        static GroupRef parse(Map<String,String> body) {
            if (!body.containsKey("groupMessageId")) return null;
            return new GroupRef(UUID.fromString(body.get("groupMessageId")), UUID.fromString(body.get("origin")), Instant.parse(body.get("originalSentAt")));
        }
    }
    public record Transfer(UUID transferId, UUID conversationId, UUID senderId, UUID recipientId,
                           String fileName, long fileSize, long transferredBytes, State state,
                           Path localPath, String error, Instant sentAt, MessageType messageType, GroupRef groupRef) {
        public Transfer(UUID transferId, UUID conversationId, UUID senderId, UUID recipientId,
                        String fileName, long fileSize, long transferredBytes, State state,
                        Path localPath, String error, Instant sentAt, MessageType messageType) {
            this(transferId,conversationId,senderId,recipientId,fileName,fileSize,transferredBytes,state,localPath,error,sentAt,messageType,null);
        }
        public Transfer(UUID transferId,UUID conversationId,UUID senderId,UUID recipientId,
                        String fileName,long fileSize,long transferredBytes,State state,
                        Path localPath,String error,Instant sentAt) {
            this(transferId,conversationId,senderId,recipientId,fileName,fileSize,transferredBytes,
                 state,localPath,error,sentAt,MessageType.FILE);
        }
    }
    @FunctionalInterface public interface Sender {
        void send(UUID peerId, WireMessage.Type type, UUID conversationId, Map<String,String> payload) throws IOException;
    }
    private final UUID localId;
    private final Sender sender;
    private final Consumer<Transfer> events;
    private final Map<UUID,Task> tasks = new ConcurrentHashMap<>();
    private final Queue<UUID> finishedIds=new ConcurrentLinkedQueue<>();
    private final Set<UUID> finishedSet=ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "intrachat-file-deadline"); t.setDaemon(true); return t;
    });
    private volatile boolean closed;

    private static final class Task {
        final UUID id, conversation, source, destination, peer;
        final String name;
        final MessageType messageType;
        final GroupRef groupRef;
        final long size;
        final Instant time;
        volatile Path sourcePath, targetPath, temporary;
        volatile long transferred, lastActivity = System.currentTimeMillis();
        volatile long lastReportedBytes;
        volatile State state;
        volatile String error;
        volatile Future<?> worker;
        OutputStream output;
        final CRC32 crc = new CRC32();
        Task(UUID id, UUID conversation, UUID source, UUID destination, String name,
             long size, Instant time, State state, UUID peer, MessageType messageType, GroupRef groupRef) {
            this.groupRef=groupRef;
            this.messageType=messageType;
            this.id=id;this.conversation=conversation;this.source=source;this.destination=destination;
            this.name=name;this.size=size;this.time=time;this.state=state;this.peer=peer;
        }
        Transfer snapshot() {
            return new Transfer(id,conversation,source,destination,name,size,transferred,state,
                sourcePath!=null?sourcePath:targetPath,error,time,messageType,groupRef);
        }
    }

    public FileTransferService(UUID localId,Sender sender,Consumer<Transfer> events) {
        this.localId=Objects.requireNonNull(localId);
        this.sender=Objects.requireNonNull(sender);
        this.events=Objects.requireNonNull(events);
        timer.scheduleAtFixedRate(this::expire,5,5,TimeUnit.SECONDS);
    }
    public static String safeFileName(String name) {
        if(name==null||name.isBlank()||name.length()>240||name.equals(".")||name.equals("..")
            ||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.indexOf(':')>=0
            ||name.chars().anyMatch(c->c<32||"<>\"|?*".indexOf(c)>=0)||name.endsWith(".")||name.endsWith(" ")
            ||name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])([.].*)?"))
            throw new IllegalArgumentException("Tên tệp không hợp lệ");
        return name;
    }
    private static boolean terminal(State state) {
        return state==State.COMPLETED||state==State.REJECTED||state==State.CANCELLED||state==State.FAILED;
    }
    private void emit(Task t) {
        try{events.accept(t.snapshot());}catch(RuntimeException ignored){}
        if(terminal(t.state)&&finishedSet.add(t.id)){
            finishedIds.add(t.id);
            while(finishedIds.size()>256){
                UUID oldest=finishedIds.poll();
                if(oldest!=null){finishedSet.remove(oldest);Task item=tasks.get(oldest);
                    if(item!=null&&terminal(item.state))tasks.remove(oldest,item);}
            }
        }
    }
    private void emitProgress(Task t){
        if(t.transferred-t.lastReportedBytes >= 256*1024||t.transferred==t.size){
            t.lastReportedBytes=t.transferred;emit(t);
        }
    }
    private void send(Task t,WireMessage.Type type,Map<String,String> body)throws IOException{
        Map<String,String> payload=new HashMap<>(body);
        payload.put("transferId",t.id.toString());
        sender.send(t.peer,type,t.conversation,payload);
    }
    private synchronized void register(Task task) throws IOException {
        if(closed)throw new IOException("File service closed");
        if(tasks.containsKey(task.id))throw new IOException("Duplicate file transfer");
        if(tasks.values().stream().anyMatch(t->t.peer.equals(task.peer)&&!terminal(t.state)))
            throw new IOException("Đang truyền một tệp với người này; hãy đợi hoặc hủy");
        tasks.put(task.id,task);
    }
    public Transfer offer(UUID peer, UUID conversation, Path source) throws IOException {
        return offer(peer,conversation,source,MessageType.FILE);
    }
    public Transfer offer(UUID peer, UUID conversation, Path source, MessageType type) throws IOException {
        return offerGroup(peer,conversation,source,type,null);
    }
    Transfer offerGroup(UUID peer, UUID conversation, Path source, MessageType type, GroupRef ref) throws IOException {
        if(type==null||!MEDIA_TYPES.contains(type))throw new IOException("Invalid media type");
        if(source==null||!Files.isRegularFile(source)||!Files.isReadable(source))
            throw new IOException("Không thể đọc tệp nguồn");
        long size=Files.size(source);
        if(size>maxSize(type))throw new IOException("Tệp vượt giới hạn "+(maxSize(type)/(1024*1024))+" MiB");
        String name=safeFileName(source.getFileName().toString());
        Task t=new Task(UUID.randomUUID(),conversation,localId,peer,name,size,Instant.now(),State.OFFERED,peer,type,ref);
        t.sourcePath=source.toAbsolutePath().normalize();
        register(t);emit(t);
        synchronized(t){t.state=State.WAITING_ACCEPT;t.lastActivity=System.currentTimeMillis();}
        emit(t);
        try{
            Map<String,String> body=new HashMap<>(Map.of("fileName",name,"fileSize",Long.toString(size),"messageType",type.name()));
            if(ref!=null){body.put("groupMessageId",ref.messageId().toString());body.put("origin",ref.origin().toString());body.put("originalSentAt",ref.sentAt().toString());}
            send(t,WireMessage.Type.FILE_OFFER,body);
        }catch(IOException failure){fail(t,failure.getMessage(),false);throw failure;}
        return t.snapshot();
    }
    boolean matches(UUID peer, UUID conversation, UUID transferId) {
        Task t=tasks.get(transferId);
        return t!=null&&t.peer.equals(peer)&&t.conversation.equals(conversation);
    }
    public Transfer get(UUID transferId){ Task t=tasks.get(transferId);return t==null?null:t.snapshot(); }
    public void accept(UUID id,Path target)throws IOException{
        Task t=required(id);
        synchronized(t){
            if(t.state!=State.WAITING_ACCEPT||!localId.equals(t.destination))
                throw new IOException("Lời mời nhận tệp không còn hiệu lực");
            if(target==null)throw new IOException("Chưa chọn nơi lưu");
            Path normalized=target.toAbsolutePath().normalize();
            if(Files.exists(normalized))throw new FileAlreadyExistsException(normalized.toString());
            Path parent=normalized.getParent();
            if(parent==null||!Files.isDirectory(parent)||!Files.isWritable(parent))
                throw new IOException("Thư mục lưu không ghi được");
            t.temporary=Files.createTempFile(parent,".intrachat-", ".part");
            try { t.output=Files.newOutputStream(t.temporary,StandardOpenOption.WRITE); }
            catch(IOException e){Files.deleteIfExists(t.temporary);t.temporary=null;throw e;}
            t.targetPath=normalized;
            t.state=State.TRANSFERRING;t.lastActivity=System.currentTimeMillis();
        }
        emit(t);
        try {send(t,WireMessage.Type.FILE_ACCEPT,Map.of());}
        catch(IOException ex){fail(t,ex.getMessage(),false);throw ex;}
    }
    public void reject(UUID id)throws IOException{
        Task t=required(id);
        synchronized(t){
            if(t.state!=State.WAITING_ACCEPT||!localId.equals(t.destination))
                throw new IOException("Không thể từ chối lời mời");
            t.state=State.REJECTED;
        }
        emit(t);send(t,WireMessage.Type.FILE_REJECT,Map.of());
    }
    public void cancel(UUID id){
        Task t=tasks.get(id);if(t==null)return;
        synchronized(t){if(terminal(t.state))return;t.state=State.CANCELLED;cleanup(t);}
        emit(t);
        try{send(t,WireMessage.Type.FILE_CANCEL,Map.of());}catch(IOException ignored){}
    }
    void failReceive(UUID id,String reason) {
        Task task=tasks.get(id);if(task!=null)fail(task,reason,true);
    }
    private Task required(UUID id)throws IOException{
        Task t=tasks.get(id);
        if(t==null)throw new IOException("Unknown transfer ID");
        return t;
    }
    private void cleanup(Task t){
        Future<?> worker=t.worker;if(worker!=null)worker.cancel(true);
        if(t.output!=null){try{t.output.close();}catch(IOException ignored){}t.output=null;}
        if(t.temporary!=null){try{Files.deleteIfExists(t.temporary);}catch(IOException ignored){}t.temporary=null;}
    }
    private void fail(Task t,String reason,boolean inform){
        synchronized(t){if(terminal(t.state))return;t.state=State.FAILED;t.error=reason;cleanup(t);}
        emit(t);
        if(inform)try{send(t,WireMessage.Type.FILE_ERROR,Map.of("reason",reason==null?"Lỗi truyền tệp":reason));}
        catch(IOException ignored){}
    }
    public void disconnected(UUID peer){
        for(Task t:tasks.values())if(t.peer.equals(peer))fail(t,"Mất kết nối TCP",false);
    }
    private void expire(){
        long now=System.currentTimeMillis();
        for(Task t:tasks.values())if(!terminal(t.state)&&now-t.lastActivity >
            (t.state==State.WAITING_ACCEPT?ACCEPT_TIMEOUT_MS:IDLE_TIMEOUT_MS))
            fail(t,"Quá thời gian chờ truyền tệp",true);
    }
    private void stream(Task t){
        CRC32 checksum=new CRC32();
        try(InputStream in=Files.newInputStream(t.sourcePath)){
            byte[] buffer=new byte[CHUNK_SIZE];int count;
            while((count=in.read(buffer))!=-1){
                if(Thread.currentThread().isInterrupted())return;
                synchronized(t){if(t.state!=State.TRANSFERRING)return;}
                if(count==0)continue;
                long offset=t.transferred;
                if(offset+count>t.size)throw new IOException("Tệp nguồn đã thay đổi kích thước");
                checksum.update(buffer,0,count);
                String encoded=Base64.getEncoder().encodeToString(Arrays.copyOf(buffer,count));
                send(t,WireMessage.Type.FILE_CHUNK,Map.of("offset",Long.toString(offset),"data",encoded));
                synchronized(t){
                    if(t.state!=State.TRANSFERRING)return;
                    t.transferred+=count;t.lastActivity=System.currentTimeMillis();
                }
                emitProgress(t);
                Thread.yield();
            }
            if(t.transferred!=t.size)throw new IOException("Tệp nguồn bị cắt trong khi gửi");
            synchronized(t){if(t.state!=State.TRANSFERRING)return;}
            send(t,WireMessage.Type.FILE_DONE,Map.of("size",Long.toString(t.size),
                "crc32",Long.toUnsignedString(checksum.getValue())));
            // Wait for receiver FILE_RESULT. Never complete on local socket write.
        }catch(Exception ex){fail(t,ex.getMessage(),true);}
    }
    public void handleIncoming(UUID peer,WireMessage frame){
        if(!frame.type().name().startsWith("FILE_"))throw new IllegalArgumentException("Not file frame");
        UUID id;
        try{id=UUID.fromString(frame.payload().get("transferId"));}
        catch(RuntimeException ex){throw new IllegalArgumentException("Invalid transferId",ex);}
        if(frame.type()==WireMessage.Type.FILE_OFFER){
            String name=safeFileName(frame.payload().get("fileName"));
            long size=Long.parseLong(frame.payload().getOrDefault("fileSize","-1"));
            if(size<0||size>MAX_FILE_SIZE||localId.equals(frame.senderId()))
                throw new IllegalArgumentException("Invalid offered file size/sender");
            MessageType contentType;
            try{contentType=MessageType.valueOf(frame.payload().getOrDefault("messageType","FILE"));}
            catch(IllegalArgumentException ex){throw new IllegalArgumentException("Invalid media type",ex);}
            if(!MEDIA_TYPES.contains(contentType)||size>maxSize(contentType))throw new IllegalArgumentException("Invalid media size/type");
            Task t=new Task(id,frame.conversationId(),frame.senderId(),localId,name,size,
                frame.sentAtUtc(),State.WAITING_ACCEPT,peer,contentType,GroupRef.parse(frame.payload()));
            try{register(t);}catch(IOException ex){
                try{send(t,WireMessage.Type.FILE_REJECT,Map.of());}catch(IOException ignored){}
                return;
            }
            emit(t);return;
        }
        Task t=tasks.get(id);
        if(t==null||!t.peer.equals(peer)||!t.conversation.equals(frame.conversationId()))
            throw new IllegalArgumentException("Unknown/mismatched file transfer");
        try{
            switch(frame.type()){
                case FILE_ACCEPT -> {
                    synchronized(t){
                        if(t.state!=State.WAITING_ACCEPT||!localId.equals(t.source))return;
                        t.state=State.TRANSFERRING;t.lastActivity=System.currentTimeMillis();
                    }
                    emit(t);
                    t.worker=workers.submit(()->stream(t));
                }
                case FILE_REJECT -> {
                    synchronized(t){if(terminal(t.state))return;t.state=State.REJECTED;cleanup(t);}
                    emit(t);
                }
                case FILE_CANCEL -> {
                    synchronized(t){if(terminal(t.state))return;t.state=State.CANCELLED;cleanup(t);}
                    emit(t);
                }
                case FILE_ERROR -> fail(t,frame.payload().getOrDefault("reason","Peer gặp lỗi"),false);
                case FILE_CHUNK -> receiveChunk(t,frame.payload());
                case FILE_DONE -> receiveDone(t,frame.payload());
                case FILE_RESULT -> {
                    synchronized(t){
                        if(t.state!=State.TRANSFERRING||!localId.equals(t.source)||t.transferred!=t.size)return;
                        t.state=State.COMPLETED;
                    }
                    emit(t);
                }
                default -> throw new IOException("Unexpected file frame");
            }
        }catch(Exception ex){fail(t,ex.getMessage(),true);}
    }
    private void receiveChunk(Task t,Map<String,String> payload)throws IOException{
        synchronized(t){
            if(t.state!=State.TRANSFERRING||!localId.equals(t.destination)||t.output==null)
                throw new IOException("Unexpected file chunk");
            long offset=Long.parseLong(payload.getOrDefault("offset","-1"));
            if(offset!=t.transferred)throw new IOException("File chunk out of order/duplicate");
            byte[] chunk;
            try{chunk=Base64.getDecoder().decode(payload.getOrDefault("data",""));}
            catch(IllegalArgumentException ex){throw new IOException("Invalid Base64 file chunk",ex);}
            if(chunk.length==0||chunk.length>CHUNK_SIZE||chunk.length>t.size-t.transferred)
                throw new IOException("Invalid file chunk length");
            t.output.write(chunk);t.crc.update(chunk);t.transferred+=chunk.length;
            t.lastActivity=System.currentTimeMillis();
        }
        emitProgress(t);
    }
    private void receiveDone(Task t,Map<String,String> payload)throws IOException{
        synchronized(t){
            if(t.state!=State.TRANSFERRING||!localId.equals(t.destination)||t.output==null)
                throw new IOException("Unexpected FILE_DONE");
            long claimed=Long.parseLong(payload.getOrDefault("size","-1"));
            long crc=Long.parseUnsignedLong(payload.getOrDefault("crc32","-1"));
            if(claimed!=t.size||t.transferred!=t.size||crc!=t.crc.getValue())
                throw new IOException("File size/CRC32 mismatch");
            t.output.close();t.output=null;
            // No REPLACE_EXISTING: race-safe against newly created destination files.
            Files.move(t.temporary,t.targetPath);t.temporary=null;
            t.state=State.COMPLETED;t.lastActivity=System.currentTimeMillis();
        }
        emit(t);
        send(t,WireMessage.Type.FILE_RESULT,Map.of());
    }
    @Override public void close(){
        closed=true;
        for(Task t:tasks.values())fail(t,"Dừng ứng dụng",false);
        timer.shutdownNow();workers.shutdownNow();
    }
}
