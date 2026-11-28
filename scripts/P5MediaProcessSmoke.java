import vn.intrachat.application.*;
import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.transport.*;
import model.MessageType;
import java.nio.file.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** P5 actual independent JVMs over TCP, including restart of both profile directories. */
public class P5MediaProcessSmoke {
 static void check(boolean ok,String detail){if(!ok)throw new AssertionError(detail);}
 static void until(BooleanSupplier pred)throws Exception{
  for(int i=0;i<800;i++){if(pred.getAsBoolean())return;Thread.sleep(20);}
  throw new AssertionError("P5 timed out");
 }
 static void pass(String s){System.out.println("PASS_"+s);System.out.flush();}
 static final String[] names={"ảnh tiếng Việt.png","photo.jpeg","movie.mp4","voice.wav","ordinary.bin","reject.mp4"};
 static MessageType type(String s){
  if(s.endsWith(".png")||s.endsWith(".jpeg"))return MessageType.IMAGE;
  if(s.endsWith(".mp4"))return MessageType.VIDEO;
  if(s.endsWith(".wav"))return MessageType.SOUND;
  return MessageType.FILE;
 }
 static Path sample(Path dir,String name,int seed)throws Exception{
  Path p=dir.resolve(name);
  int size=name.equals("movie.mp4")?FileTransferService.CHUNK_SIZE*18+7:FileTransferService.CHUNK_SIZE*2+seed;
  byte[] b=new byte[size];new Random(seed).nextBytes(b);Files.write(p,b);return p;
 }
 static void peer(String role,int port,Path dir)throws Exception{
  LocalProfile profile=LocalProfile.loadOrCreate(dir);
  ConcurrentHashMap<String,FileTransferService.Transfer> transfers=new ConcurrentHashMap<>();
  try(LiveChatService tcp=new LiveChatService(profile,false);
      LanCoordinator lan=new LanCoordinator(profile,info->CompletableFuture.completedFuture(true),
        peer->{try{tcp.attach(peer.session(),peer.name());}catch(IOException e){throw new IllegalStateException(e);}})){
   tcp.addFileListener(t->{
    transfers.put(t.fileName(),t);
    if(role.equals("B")&&t.state()==FileTransferService.State.WAITING_ACCEPT)
     Thread.ofVirtual().start(()->{try{
      if(t.fileName().equals("reject.mp4"))tcp.files().reject(t.transferId());
      else tcp.files().accept(t.transferId(),dir.resolve("received-"+t.fileName()));
     }catch(Exception e){e.printStackTrace();System.exit(2);}});
   });
   if(role.equals("B")){
    lan.startListening(new NetworkConfig("127.0.0.1",port));pass("LISTEN");
    until(()->!tcp.connectedPeers().isEmpty());
    for(int i=0;i<names.length;i++){
     final String name=names[i];
     final FileTransferService.State target=name.equals("reject.mp4")
       ?FileTransferService.State.REJECTED:FileTransferService.State.COMPLETED;
     until(()->transfers.containsKey(name)&&transfers.get(name).state()==target);
     var t=transfers.get(name);
     check(t.messageType()==type(name),"Receiver type "+name);
     check(t.senderId()!=null&&t.sentAt()!=null&&t.transferId()!=null,"Metadata "+name);
     if(target==FileTransferService.State.COMPLETED){
      check(Files.size(dir.resolve("received-"+name))==t.fileSize(),"Size "+name);
      pass("RECEIVED_"+i);
     }else check(!Files.exists(dir.resolve("received-"+name)),"Reject published file");
    }
   }else {
    var peer=lan.connect(new NetworkConfig("127.0.0.1",port)).get(10,TimeUnit.SECONDS);
    UUID remote=peer.session().remoteId(),conversation=tcp.conversationId(remote);
    for(int i=0;i<names.length;i++){
     String name=names[i];Path src=sample(dir,name,i+11);
     var t=tcp.files().offer(remote,conversation,src,type(name));
     FileTransferService.State target=name.equals("reject.mp4")
      ?FileTransferService.State.REJECTED:FileTransferService.State.COMPLETED;
     until(()->transfers.containsKey(name)&&transfers.get(name).state()==target);
     check(transfers.get(name).messageType()==type(name),"Sender type "+name);
     pass("SENT_"+i);
    }
   }
  }
 }
 static void restore(String role,Path dir)throws Exception{
  LocalProfile p=LocalProfile.loadOrCreate(dir);
  try(var repo=new HistoryRepository(p.dataDir())){
   var all=repo.conversations().stream().flatMap(id->repo.loadMessages(id,500,0).stream()).toList();
   check(all.size()==names.length,"Restored count "+all.size());
   for(String name:names){
    var entries=all.stream().filter(e->name.equals(e.fileName())).toList();
    check(entries.size()==1,"Duplicate/missing "+name);
    var e=entries.get(0);
    check(e.messageType().equals(type(name).name()),"Restored type "+name);
    check(e.transferId().equals(e.messageId()),"Restored ID");
    check(e.sentAtUtc()!=null&&e.senderId()!=null,"Restored sender/time");
    check(e.transferState().equals(name.equals("reject.mp4")?"REJECTED":"COMPLETED"),"Restored state "+name);
    if(role.equals("B")&&!name.equals("reject.mp4"))
     check(Files.isRegularFile(Path.of(e.localPath())),"Missing receiver local path "+name);
   }
   pass("RESTORE_"+role);
  }
 }
 static void orchestrate(Path dir)throws Exception{
  Files.createDirectories(dir);
  int port;try(ServerSocket s=new ServerSocket(0,50,InetAddress.getLoopbackAddress())){port=s.getLocalPort();}
  String javaExe=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
  String cp=System.getProperty("java.class.path");
  String source=Path.of("scripts","P5MediaProcessSmoke.java").toAbsolutePath().toString();
  List<Process> jobs=new ArrayList<>();
  try{
   for(String role:List.of("B","A")){
    Process child=new ProcessBuilder(javaExe,"-cp",cp,source,role,Integer.toString(port),dir.resolve(role).toString())
      .redirectOutput(dir.resolve(role+".out").toFile()).redirectError(dir.resolve(role+".err").toFile()).start();
    jobs.add(child);
    if(role.equals("B"))Thread.sleep(2300);
   }
   for(int i=0;i<jobs.size();i++){
    String role=i==0?"B":"A";Process child=jobs.get(i);
    check(child.waitFor(90,TimeUnit.SECONDS),"Process timed out "+role);
    String out=Files.readString(dir.resolve(role+".out")),err=Files.readString(dir.resolve(role+".err"));
    check(child.exitValue()==0&&out.contains("PASS_"+(role.equals("B")?"RECEIVED_4":"SENT_5")),
      role+" failed code="+child.exitValue()+" out="+out+" err="+err);
    System.out.print(out);
   }
   for(String name:names)if(!name.equals("reject.mp4")){
    byte[] original=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dir.resolve("A").resolve(name)));
    byte[] received=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dir.resolve("B").resolve("received-"+name)));
    check(Arrays.equals(original,received),"SHA256 mismatch "+name);
   }
   pass("TWO_JVM_SHA256");
   for(String role:List.of("A","B")){
    Process restart=new ProcessBuilder(javaExe,"-cp",cp,source,"restore",role,dir.resolve(role).toString())
      .redirectOutput(dir.resolve(role+"-restore.out").toFile())
      .redirectError(dir.resolve(role+"-restore.err").toFile()).start();
    check(restart.waitFor(20,TimeUnit.SECONDS)&&restart.exitValue()==0,
      "Restart failed "+role+" "+Files.readString(dir.resolve(role+"-restore.err")));
    System.out.print(Files.readString(dir.resolve(role+"-restore.out")));
   }
   pass("P5_PROCESS");
  }finally{for(Process p:jobs)if(p.isAlive())p.destroyForcibly();}
 }
 public static void main(String[] args)throws Exception{
  if(args[0].equals("run"))orchestrate(Path.of(args[1]));
  else if(args[0].equals("restore"))restore(args[1],Path.of(args[2]));
  else peer(args[0],Integer.parseInt(args[1]),Path.of(args[2]));
 }
}
