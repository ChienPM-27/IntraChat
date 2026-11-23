import vn.intrachat.application.*;
import vn.intrachat.transport.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.zip.CRC32;

/** Independent JVM A/B; no mock file payload: real LanSession and TCP loopback. */
public class P3FileProcessSmoke {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void until(BooleanSupplier condition)throws Exception{
        for(int i=0;i<1000;i++){if(condition.getAsBoolean())return;Thread.sleep(20);}
        throw new AssertionError("P3 timed out");
    }
    static Path source(Path dir,String name,int size,int seed)throws Exception{
        Path path=dir.resolve(name);Random random=new Random(seed);
        byte[] buffer=new byte[8192];int remaining=size;
        try(OutputStream out=Files.newOutputStream(path)){
            while(remaining>0){random.nextBytes(buffer);int n=Math.min(buffer.length,remaining);out.write(buffer,0,n);remaining-=n;}
        }
        return path;
    }
    static long crc(Path file)throws Exception{
        CRC32 crc=new CRC32();try(InputStream in=Files.newInputStream(file)){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)crc.update(buffer,0,n);
        }
        return crc.getValue();
    }
    static void passed(String name){System.out.println("PASS_"+name);System.out.flush();}
    public static void main(String[] args)throws Exception{
        String role=args[0];int port=Integer.parseInt(args[1]);Path dir=Path.of(args[2]);
        LocalProfile profile=LocalProfile.loadOrCreate(dir);
        AtomicReference<LiveChatService> holder=new AtomicReference<>();
        AtomicReference<UUID> remoteId=new AtomicReference<>();
        ConcurrentHashMap<String,FileTransferService.Transfer> current=new ConcurrentHashMap<>();
        AtomicBoolean senderCancel=new AtomicBoolean(),receiverCancel=new AtomicBoolean(),disconnect=new AtomicBoolean();
        try(LiveChatService tcp=new LiveChatService(profile);
            LanCoordinator lan=new LanCoordinator(profile,i->CompletableFuture.completedFuture(true),
                peer->{try{tcp.attach(peer.session(),peer.name());}
                    catch(IOException ex){throw new IllegalStateException(ex);}})){
            holder.set(tcp);
            tcp.addFileListener(t->{
                current.put(t.fileName(),t);
                try{
                    if(role.equals("B")&&t.state()==FileTransferService.State.WAITING_ACCEPT){
                        Thread.ofVirtual().start(()->{
                            try{
                                if(t.fileName().startsWith("reject"))tcp.files().reject(t.transferId());
                                else tcp.files().accept(t.transferId(),dir.resolve("received-"+t.fileName()));
                            }catch(Exception error){error.printStackTrace();System.exit(2);}
                        });
                    }
                    if(role.equals("A")&&t.fileName().startsWith("cancel-A")&&
                        t.state()==FileTransferService.State.TRANSFERRING&&t.transferredBytes()>0&&senderCancel.compareAndSet(false,true))
                        Thread.ofVirtual().start(()->tcp.files().cancel(t.transferId()));
                    if(role.equals("B")&&t.fileName().startsWith("cancel-B")&&
                        t.state()==FileTransferService.State.TRANSFERRING&&t.transferredBytes()>0&&receiverCancel.compareAndSet(false,true))
                        Thread.ofVirtual().start(()->tcp.files().cancel(t.transferId()));
                    if(role.equals("A")&&t.fileName().startsWith("disconnect")&&
                        t.state()==FileTransferService.State.TRANSFERRING&&t.transferredBytes()>0&&disconnect.compareAndSet(false,true))
                        Thread.ofVirtual().start(()->tcp.disconnect(t.recipientId()));
                }catch(Exception error){error.printStackTrace();}
            });
            if(role.equals("B")){
                lan.startListening(new NetworkConfig("127.0.0.1",port));passed("LISTENING");
                until(()->!tcp.connectedPeers().isEmpty());
                until(()->current.containsKey("small.bin")&&current.get("small.bin").state()==FileTransferService.State.COMPLETED);
                check(Files.size(dir.resolve("received-small.bin"))==317,"Small size");
                check(crc(dir.resolve("received-small.bin"))==crc(source(dir,"expect-small.bin",317,9)),"Small bytes");
                passed("SMALL_BYTES");
                until(()->current.containsKey("many.bin")&&current.get("many.bin").state()==FileTransferService.State.COMPLETED);
                check(crc(dir.resolve("received-many.bin"))==crc(source(dir,"expect-many.bin",FileTransferService.CHUNK_SIZE*13+31,27)),"Many CRC");
                passed("MANY_CHUNKS");
                String unicode="Tiếng Việt có dấu 🇻🇳.bin";
                until(()->current.containsKey(unicode)&&current.get(unicode).state()==FileTransferService.State.COMPLETED);
                check(Files.exists(dir.resolve("received-"+unicode)),"Unicode name");
                check(crc(dir.resolve("received-"+unicode))==crc(source(dir,"expect-unicode.bin",9011,56)),"Unicode bytes");
                passed("UNICODE");
                until(()->current.containsKey("reject.bin")&&current.get("reject.bin").state()==FileTransferService.State.REJECTED);
                check(!Files.exists(dir.resolve("received-reject.bin")),"Reject output exists");passed("REJECT");
                until(()->current.containsKey("cancel-A.bin")&&current.get("cancel-A.bin").state()==FileTransferService.State.CANCELLED);
                check(!Files.exists(dir.resolve("received-cancel-A.bin")),"Cancel A output");passed("CANCEL_A");
                until(()->current.containsKey("cancel-B.bin")&&current.get("cancel-B.bin").state()==FileTransferService.State.CANCELLED);
                check(!Files.exists(dir.resolve("received-cancel-B.bin")),"Cancel B output");passed("CANCEL_B");
                until(()->current.containsKey("interleave.bin")&&current.get("interleave.bin").state()==FileTransferService.State.COMPLETED);
                until(()->tcp.connectedPeers().keySet().stream().anyMatch(peer->tcp.transcript(peer).stream()
                    .anyMatch(m->m.text().equals("Text while file active 😀"))));
                passed("TEXT_DURING_FILE");
                until(()->current.containsKey("disconnect.bin")&&current.get("disconnect.bin").state()==FileTransferService.State.FAILED);
                check(!Files.exists(dir.resolve("received-disconnect.bin")),"Disconnect published");
                try(var list=Files.list(dir)){check(list.noneMatch(f->f.toString().endsWith(".part")),"Temporary file leaked");}
                passed("DISCONNECT_AND_CLEANUP");
            }else{
                var peer=lan.connect(new NetworkConfig("127.0.0.1",port)).get(10,TimeUnit.SECONDS);
                UUID b=peer.session().remoteId();remoteId.set(b);UUID conversation=tcp.conversationId(b);
                for(String name:new String[]{"small.bin","many.bin","Tiếng Việt có dấu 🇻🇳.bin","reject.bin",
                    "cancel-A.bin","cancel-B.bin","interleave.bin","disconnect.bin"}){
                    int size=switch(name){
                        case "small.bin"->317;
                        case "many.bin"->FileTransferService.CHUNK_SIZE*13+31;
                        case "Tiếng Việt có dấu 🇻🇳.bin"->9011;
                        case "reject.bin"->512;
                        default->32*1024*1024;
                    };
                    int seed=switch(name){case "small.bin"->9;case "many.bin"->27;case "Tiếng Việt có dấu 🇻🇳.bin"->56;default->42;};
                    Path src=source(dir,name,size,seed);
                    var transfer=tcp.files().offer(b,conversation,src);
                    if(name.equals("interleave.bin")){
                        until(()->current.get(name).state()==FileTransferService.State.TRANSFERRING);
                        tcp.sendText(b,"Text while file active 😀").get(8,TimeUnit.SECONDS);
                    }
                    FileTransferService.State wanted=name.equals("reject.bin")?FileTransferService.State.REJECTED:
                        name.startsWith("cancel-")?FileTransferService.State.CANCELLED:
                        name.equals("disconnect.bin")?FileTransferService.State.FAILED:
                        FileTransferService.State.COMPLETED;
                    until(()->current.get(name).state()==wanted);
                    passed(name.replaceAll("[^a-zA-Z]","_").toUpperCase(Locale.ROOT));
                    if(name.equals("disconnect.bin"))break;
                }
            }
        }
    }
}
