import vn.intrachat.application.*;
import vn.intrachat.domain.*;
import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.protocol.WireMessage;
import vn.intrachat.transport.*;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * P4 independent-process smoke. Each invocation is a new OS JVM.
 * Modes: listen / connect / restore / offline / continue-listen / continue-connect / restore-group.
 */
public class P4HistoryProcessSmoke {
    static void ok(boolean condition,String detail){if(!condition)throw new AssertionError(detail);}
    static void until(BooleanSupplier condition)throws Exception{
        for(int i=0;i<500;i++){if(condition.getAsBoolean())return;Thread.sleep(30);}
        throw new AssertionError("P4 process timeout");
    }
    static HistoryRepository.Entry find(HistoryRepository repo,String body) {
        return repo.conversations().stream().flatMap(cid->repo.loadMessages(cid,500,0).stream())
            .filter(e->body.equals(e.text())).findFirst().orElseThrow(()->new AssertionError("Missing persisted "+body));
    }
    static void assertLocal(LocalProfile profile,HistoryRepository repo,String role,boolean file) {
        var one=find(repo,"Xin chào B 👋");
        var two=find(repo,"Mình đã nhận được 😀");
        ok(!one.messageId().equals(two.messageId()),"same message id");
        ok(one.conversationId().equals(two.conversationId()),"conversation split");
        ok(one.sentAtUtc()!=null&&two.sentAtUtc()!=null,"UTC time lost");
        ok(!one.senderId().equals(two.senderId()),"sender identity wrong");
        ok(repo.loadMessages(one.conversationId(),500,0).stream()
            .filter(e->e.messageId().equals(one.messageId())).count()==1,"duplicate after reload");
        ok(repo.peers().size()==1,"offline contact lost");
        {
            var binary=repo.loadMessages(one.conversationId(),500,0).stream()
                .filter(e->e.messageType().equals("FILE")).findFirst().orElseThrow();
            ok(binary.fileName().equals("p4-sample.dat")&&binary.fileSize()==2048,"file metadata lost");
            ok(binary.transferState().equals("COMPLETED"),"file not completed: "+binary.transferState());
            if(file&&role.equals("B"))ok(Files.isRegularFile(Path.of(binary.localPath())),"receiver file missing");
        }
        System.out.println("PASS_RESTORED_"+role+" "+profile.deviceId()+" "+one.conversationId());
    }
    static void orchestrate(Path root,boolean continueMode)throws Exception{
        Files.createDirectories(root);
        int port;
        try(ServerSocket portFinder=new ServerSocket(0,50,InetAddress.getLoopbackAddress())){
            port=portFinder.getLocalPort();
        }
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        String cp=System.getProperty("java.class.path");
        String listener=continueMode?"continue-listen":"listen";
        String connector=continueMode?"continue-connect":"connect";
        List<Process> processes=new ArrayList<>();
        try{
            Process b=new ProcessBuilder(java,"-cp",cp,"P4HistoryProcessSmoke",listener,
                Integer.toString(port),root.resolve("profile-B").toString())
                .redirectOutput(root.resolve(listener+".out").toFile())
                .redirectError(root.resolve(listener+".err").toFile()).start();
            processes.add(b);
            Thread.sleep(1800);
            Process a=new ProcessBuilder(java,"-cp",cp,"P4HistoryProcessSmoke",connector,
                Integer.toString(port),root.resolve("profile-A").toString())
                .redirectOutput(root.resolve(connector+".out").toFile())
                .redirectError(root.resolve(connector+".err").toFile()).start();
            processes.add(a);
            for(int i=0;i<processes.size();i++){
                Process process=processes.get(i);
                String role=i==0?listener:connector;
                if(!process.waitFor(45,TimeUnit.SECONDS))throw new AssertionError(role+" process timeout");
                String out=Files.readString(root.resolve(role+".out"));
                String err=Files.readString(root.resolve(role+".err"));
                if(process.exitValue()!=0||!out.contains("PASS_")||!err.isBlank())
                    throw new AssertionError(role+" exit="+process.exitValue()+" out="+out+" err="+err);
                System.out.println(out.trim());
            }
            System.out.println(continueMode?"PASS_TWO_JVM_CONTINUED":"PASS_TWO_JVM_INITIAL");
        }finally{
            for(Process child:processes)if(child.isAlive()){
                child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);
            }
        }
    }
    static void orchestrateGroup(Path root)throws Exception{
        Files.createDirectories(root);
        int port;
        try(ServerSocket finder=new ServerSocket(0,50,InetAddress.getLoopbackAddress())){port=finder.getLocalPort();}
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        String cp=System.getProperty("java.class.path");
        List<Process> children=new ArrayList<>();
        try{
            for(String role:List.of("A","B","C")){
                Process p=new ProcessBuilder(java,"-cp",cp,"P2GroupProcessSmoke",role,
                    Integer.toString(port),root.resolve("profile-"+role).toString())
                    .redirectOutput(root.resolve(role+".out").toFile())
                    .redirectError(root.resolve(role+".err").toFile()).start();
                children.add(p);
                Thread.sleep(role.equals("A")?1800:700);
            }
            Map<String,String> canonical=new HashMap<>();
            int count=0;
            for(int i=0;i<children.size();i++){
                String role="ABC".substring(i,i+1);
                Process p=children.get(i);
                if(!p.waitFor(60,TimeUnit.SECONDS))throw new AssertionError("P2 group timeout "+role);
                String out=Files.readString(root.resolve(role+".out"));
                String err=Files.readString(root.resolve(role+".err"));
                if(p.exitValue()!=0||!out.contains("PASS_")||!err.isBlank())
                    throw new AssertionError("P2 group "+role+" exit="+p.exitValue()+" out="+out+" err="+err);
                for(String row:out.lines().toList()){
                    if(!row.startsWith("TRACE|"))continue;
                    String[] fields=row.split("[|]",-1);
                    if(fields.length!=7||!fields[1].equals(role))throw new AssertionError("Bad group TRACE");
                    String key=fields[2];
                    String value=String.join("|",Arrays.copyOfRange(fields,3,7));
                    if(canonical.containsKey(key)&&!canonical.get(key).equals(value))
                        throw new AssertionError("Cross-process group sender/time mismatch");
                    canonical.put(key,value);count++;
                }
                System.out.println("GROUP_"+role+"_PASS");
            }
            ok(count==9&&canonical.size()==3,"Missing cross-JVM group traces");
            System.out.println("PASS_THREE_JVM_GROUP");
        }finally{
            for(Process child:children)if(child.isAlive()){
                child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        String mode=args[0];
        if(mode.equals("orchestrate-group")){
            orchestrateGroup(Path.of(args[2]));return;
        }
        if(mode.equals("orchestrate")){
            orchestrate(Path.of(args[2]),args.length>3&&args[3].equals("continue"));
            return;
        }
        int port=Integer.parseInt(args[1]);
        Path dir=Path.of(args[2]);
        LocalProfile local=LocalProfile.loadOrCreate(dir);
        if(mode.equals("restore")||mode.equals("offline")||mode.equals("missing")){
            try(LiveChatService svc=new LiveChatService(local)){
                assertLocal(local,svc.history(),args[3],!mode.equals("missing"));
                UUID remote=svc.history().peers().keySet().iterator().next();
                ok(svc.connectedPeers().isEmpty(),"unexpected online session");
                ok(svc.transcript(remote).size()>=2&&svc.transcript(remote).size()<=3,"P2P transcript not restored");
                System.out.println("PASS_OFFLINE_HISTORY");
            }
            return;
        }
        if(mode.equals("restore-group")){
            try(LiveChatService svc=new LiveChatService(local);
                GroupService group=new GroupService(svc,(owner,name,title)->CompletableFuture.completedFuture(false))){
                var original=find(svc.history(),"Xin chào nhóm 👋");
                var members=group.groups();
                ok(members.stream().anyMatch(g->g.groupId().equals(original.conversationId())),"group metadata missing");
                var msgs=group.history(original.conversationId());
                ok(msgs.size()>=3&&msgs.stream().anyMatch(m->m.text().equals("Mình là B 😀"))&&msgs.stream().anyMatch(m->m.text().equals("C gửi tiếng Việt 🇻🇳")),"three local group messages not restored: "+msgs.size());
                ok(msgs.stream().map(GroupService.Message::senderId).distinct().count()==3,"original sender missing");
                ok(msgs.stream().allMatch(m->m.sentAt()!=null),"group timestamp lost");
                try{group.sendGroupText(original.conversationId(),"Not authorized").toCompletableFuture().get(1,TimeUnit.SECONDS);
                    throw new AssertionError("Unverified restored roster can send");}
                catch(ExecutionException rejected){/* expected until owner roster verified */}
                System.out.println("PASS_GROUP_RESTORED "+local.deviceId()+" "+original.conversationId());
            }
            return;
        }
        boolean listener=mode.equals("listen")||mode.equals("continue-listen");
        boolean continuing=mode.startsWith("continue");
        try(LiveChatService svc=new LiveChatService(local);
            LanCoordinator lan=new LanCoordinator(local,incoming->CompletableFuture.completedFuture(true),
                peer->{try{svc.attach(peer.session(),peer.name());}catch(Exception e){throw new IllegalStateException(e);}})){
            if(listener){
                if(!continuing){
                    svc.addFileListener(t->{
                        if(t.state()==FileTransferService.State.WAITING_ACCEPT)
                            Thread.ofVirtual().start(()->{
                                try{svc.files().accept(t.transferId(),dir.resolve("received-"+t.fileName()));}
                                catch(Exception e){e.printStackTrace();}
                            });
                    });
                }
                lan.startListening(new NetworkConfig("127.0.0.1",port));
                System.out.println("READY");System.out.flush();
                until(()->svc.connectedPeers().size()==1);
                UUID other=svc.connectedPeers().keySet().iterator().next();
                if(continuing){
                    until(()->svc.transcript(other).stream().anyMatch(m->m.text().equals("Tiếp tục sau restart 🎉")));
                    ok(svc.transcript(other).stream().filter(m->m.text().equals("Xin chào B 👋")).count()==1,"duplicate replay on B");
                    ok(svc.transcript(other).size()==3,"B continuation wrong count");
                    System.out.println("PASS_CONTINUED_LISTENER");
                } else {
                    until(()->svc.transcript(other).stream().anyMatch(m->m.text().equals("Xin chào B 👋")));
                    var incoming=svc.transcript(other).stream().filter(m->m.text().equals("Xin chào B 👋")).findFirst().orElseThrow();
                    ok(svc.markViewed(other,incoming.messageId(),incoming.conversationId()),"READ not sent");
                    svc.sendText(other,"Mình đã nhận được 😀").get(8,TimeUnit.SECONDS);
                    until(()->svc.history().conversations().stream().flatMap(c->svc.history().loadMessages(c,500,0).stream())
                        .anyMatch(e->e.messageType().equals("FILE")&&e.transferState().equals("COMPLETED")));
                    System.out.println("PASS_LISTENER_FILE_READ");
                }
            }else{
                var peer=lan.connect(new NetworkConfig("127.0.0.1",port)).get(10,TimeUnit.SECONDS);
                UUID other=peer.session().remoteId();
                if(continuing){
                    var original=find(svc.history(),"Xin chào B 👋");
                    peer.session().send(new WireMessage(WireMessage.Type.TEXT,original.messageId(),original.conversationId(),
                        local.deviceId(),other,original.sentAtUtc(),Map.of("text",original.text())));
                    var next=svc.sendText(other,"Tiếp tục sau restart 🎉").get(8,TimeUnit.SECONDS);
                    until(()->svc.history().find(next.messageId())!=null);
                    ok(next.conversationId().equals(original.conversationId()),"new conversation after restart");
                    System.out.println("PASS_CONTINUED_CONNECTOR");
                    Thread.sleep(900);
                }else{
                    var sent=svc.sendText(other,"Xin chào B 👋").get(8,TimeUnit.SECONDS);
                    until(()->svc.status(sent.messageId())==MessageAttempt.Status.READ);
                    until(()->svc.transcript(other).stream().anyMatch(m->m.text().equals("Mình đã nhận được 😀")));
                    Path source=dir.resolve("p4-sample.dat");byte[] bytes=new byte[2048];new Random(17).nextBytes(bytes);
                    Files.write(source,bytes);
                    UUID conversation=svc.conversationId(other);
                    var transfer=svc.files().offer(other,conversation,source);
                    until(()->svc.files().get(transfer.transferId())!=null&&
                        svc.files().get(transfer.transferId()).state()==FileTransferService.State.COMPLETED);
                    System.out.println("PASS_CONNECTOR_ACK_READ_FILE");
                    Thread.sleep(600);
                }
            }
        }
    }
}
