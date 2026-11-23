import vn.intrachat.application.*;
import vn.intrachat.transport.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Run separately as owner, B and C: three OS JVMs and genuine TCP sessions. */
public class P2GroupProcessSmoke {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static void until(java.util.function.BooleanSupplier ready)throws Exception{
        for(int i=0;i<500;i++){if(ready.getAsBoolean())return;Thread.sleep(40);}
        throw new AssertionError("Timeout waiting for group TCP event");
    }
    static boolean has(GroupService group,UUID id,String text){
        return group.history(id).stream().anyMatch(m->m.text().equals(text));
    }
    static void trace(String role,GroupService group,UUID id){
        String[] samples={"Xin chào nhóm 👋","Mình là B 😀","C gửi tiếng Việt 🇻🇳"};
        for(int i=0;i<samples.length;i++){
            String key="ABC".substring(i,i+1);
            String sample=samples[i];
            GroupService.Message msg=group.history(id).stream().filter(m->m.text().equals(sample)).findFirst().orElseThrow();
            System.out.println("TRACE|"+role+"|"+key+"|"+id+"|"+msg.messageId()+"|"+msg.senderId()+"|"+msg.sentAt());
        }
    }
    public static void main(String[] args)throws Exception{
        String role=args[0];int port=Integer.parseInt(args[1]);
        LocalProfile profile=LocalProfile.loadOrCreate(Path.of(args[2]));
        Map<UUID,Integer> acks=new ConcurrentHashMap<>();
        Map<UUID,Set<Integer>> ackSteps=new ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicBoolean rejected=new java.util.concurrent.atomic.AtomicBoolean();
        try(LiveChatService tcp=new LiveChatService(profile);
            GroupService group=new GroupService(tcp,(owner,label,name)->CompletableFuture.completedFuture(!role.equals("C")||!name.equals("Reject group")));
            LanCoordinator lan=new LanCoordinator(profile,i->CompletableFuture.completedFuture(true),
                peer->{try{tcp.attach(peer.session(),peer.name());}catch(Exception e){throw new IllegalStateException(e);}})){
            group.addListener(e->{if(e.messageId()!=null){acks.put(e.messageId(),e.received()); ackSteps.computeIfAbsent(e.messageId(),x->ConcurrentHashMap.newKeySet()).add(e.received());} if(e.notice()!=null&&e.notice().contains("từ chối"))rejected.set(true); if(e.group()!=null && e.message()==null)System.out.println("ROSTER "+role+" "+e.group().revision()+" "+e.group().memberIds()+" "+e.notice());});
            UUID ownerId=null;
            if(role.equals("A")){
                lan.startListening(new NetworkConfig("127.0.0.1",port));
                System.out.println("OWNER_LISTENING");System.out.flush();
                until(()->tcp.connectedPeers().size()==2);
                List<UUID> peers=new ArrayList<>(tcp.connectedPeers().keySet());
                var created=group.createGroup("Nhóm ABC",peers).toCompletableFuture().get(12,TimeUnit.SECONDS);
                UUID id=created.groupId();
                until(()->group.group(id).memberIds().size()==3);
                var sent=group.sendGroupText(id,"Xin chào nhóm 👋").toCompletableFuture().get(8,TimeUnit.SECONDS);
                until(()->acks.getOrDefault(sent.messageId(),0)==2);
                check(ackSteps.get(sent.messageId()).containsAll(Set.of(1,2)),"No independent recipient receipts");
                until(()->has(group,id,"Mình là B 😀"));
                until(()->has(group,id,"C gửi tiếng Việt 🇻🇳"));
                check(group.history(id).stream().anyMatch(m->m.senderId().equals(profile.deviceId())),"A sender");
                check(group.history(id).stream().anyMatch(m->m.text().equals("Mình là B 😀")&&peers.contains(m.senderId())),"B sender");
                check(group.history(id).stream().anyMatch(m->m.text().equals("C gửi tiếng Việt 🇻🇳")&&peers.contains(m.senderId())),"C sender");
                check(group.history(id).stream().filter(m->m.text().equals("Mình là B 😀")).count()==1,
                    "Duplicate message on owner");
                trace(role,group,id);
                UUID bId=group.history(id).stream().filter(m->m.text().equals("Mình là B 😀")).findFirst().orElseThrow().senderId();
                UUID cId=group.history(id).stream().filter(m->m.text().equals("C gửi tiếng Việt 🇻🇳")).findFirst().orElseThrow().senderId();
                var second=group.createGroup("Reject group",List.of(bId,cId)).toCompletableFuture().get(8,TimeUnit.SECONDS);
                UUID other=second.groupId();
                check(!other.equals(id),"Groups share same ID");
                until(rejected::get);
                check(group.group(other).memberIds().equals(List.of(profile.deviceId(),bId)),"Reject roster incorrect");
                until(()->has(group,other,"Nhóm thứ hai"));
                until(()->group.group(other).memberIds().size()==1);
                until(()->tcp.transcript(bId).stream().anyMatch(m->m.text().equals("P1 direct 😀")));
                group.remove(id,cId).toCompletableFuture().get(8,TimeUnit.SECONDS);
                check(!group.group(id).contains(cId),"Removed C still in roster");
                group.sendGroupText(id,"Sau khi loại C").toCompletableFuture().get(8,TimeUnit.SECONDS);
                var third=group.createGroup("Offline check",List.of(bId,cId)).toCompletableFuture().get(8,TimeUnit.SECONDS);
                UUID offlineGroup=third.groupId();
                until(()->group.group(offlineGroup).memberIds().size()==3);
                group.sendGroupText(offlineGroup,"OFFLINE_SETUP").toCompletableFuture().get(8,TimeUnit.SECONDS);
                until(()->!tcp.connectedPeers().containsKey(cId));
                try{group.sendGroupText(offlineGroup,"B vẫn nhận khi C offline").toCompletableFuture().get(8,TimeUnit.SECONDS);
                    throw new AssertionError("Expected partial fanout failure");}
                catch(ExecutionException expectedFailure){/* C offline, B remains reachable */}
                UUID partial=group.history(offlineGroup).stream().filter(m->m.text().equals("B vẫn nhận khi C offline"))
                    .findFirst().orElseThrow().messageId();
                until(()->acks.getOrDefault(partial,0)==1);
                check(acks.get(partial)==1,"Reported offline C as received");
                System.out.println("PASS_OWNER_3_MEMBERS_RELAY_ACK_REJECT_LEAVE_REMOVE_P2P");
            } else {
                var owner=lan.connect(new NetworkConfig("127.0.0.1",port)).get(10,TimeUnit.SECONDS);
                ownerId=owner.session().remoteId();
                until(()->!group.groups().isEmpty());
                UUID oid=ownerId;
                until(()->group.groups().stream().anyMatch(g->g.memberIds().size()==3));
                UUID id=group.groups().get(0).groupId();
                check(group.group(id).memberIds().stream().allMatch(m->group.memberName(m)!=null),
                    "Missing relayed member display name");
                until(()->has(group,id,"Xin chào nhóm 👋"));
                if(role.equals("B")){
                    var sentB=group.sendGroupText(id,"Mình là B 😀").toCompletableFuture().get(8,TimeUnit.SECONDS);
                    tcp.sendGroup(oid,vn.intrachat.protocol.WireMessage.Type.GROUP_TEXT,id,Map.of(
                        "originalMessageId",sentB.messageId().toString(),"origin",sentB.senderId().toString(),
                        "sentAt",sentB.sentAt().toString(),"text",sentB.text())).get(8,TimeUnit.SECONDS);
                    until(()->acks.values().stream().anyMatch(n->n==2));
                    until(()->has(group,id,"C gửi tiếng Việt 🇻🇳"));
                    trace(role,group,id);
                    check(group.history(id).stream().filter(m->m.text().equals("C gửi tiếng Việt 🇻🇳"))
                        .allMatch(m->!m.senderId().equals(oid)),"C original sender");
                    until(()->group.groups().stream().anyMatch(g->g.groupName().equals("Reject group")));
                    UUID second=group.groups().stream().filter(g->g.groupName().equals("Reject group")).findFirst().orElseThrow().groupId();
                    group.sendGroupText(second,"Nhóm thứ hai").toCompletableFuture().get(8,TimeUnit.SECONDS);
                    group.leave(second).toCompletableFuture().get(8,TimeUnit.SECONDS);
                    check(group.group(second)==null,"Leave did not clear local group");
                    tcp.sendText(oid,"P1 direct 😀").get(8,TimeUnit.SECONDS);
                    until(()->has(group,id,"Sau khi loại C"));
                    until(()->group.groups().stream().anyMatch(g->g.groupName().equals("Offline check")));
                    UUID offlineGroup=group.groups().stream().filter(g->g.groupName().equals("Offline check"))
                        .findFirst().orElseThrow().groupId();
                    until(()->has(group,offlineGroup,"B vẫn nhận khi C offline"));
                    until(()->!tcp.connectedPeers().containsKey(oid));
                    try{group.sendGroupText(id,"Owner offline").toCompletableFuture().get(3,TimeUnit.SECONDS);
                        throw new AssertionError("Owner offline accepted group send");}
                    catch(ExecutionException expected){/* offline properly rejected */}
                    System.out.println("PASS_B_RELAY_ACK_LEAVE_P2P_OWNER_OFFLINE");
                } else {
                    until(()->has(group,id,"Mình là B 😀"));
                    check(group.history(id).stream().filter(m->m.text().equals("Mình là B 😀"))
                        .allMatch(m->!m.senderId().equals(oid)),"B original sender");
                    group.sendGroupText(id,"C gửi tiếng Việt 🇻🇳").toCompletableFuture().get(8,TimeUnit.SECONDS);
                    until(()->acks.values().stream().anyMatch(n->n==2));
                    trace(role,group,id);
                    until(()->group.group(id)==null);
                    Thread.sleep(400);
                    check(group.history(id).stream().noneMatch(m->m.text().equals("Sau khi loại C")),"Removed member received text");
                    until(()->group.groups().stream().anyMatch(g->g.groupName().equals("Offline check")));
                    UUID offlineGroup=group.groups().stream().filter(g->g.groupName().equals("Offline check"))
                        .findFirst().orElseThrow().groupId();
                    until(()->has(group,offlineGroup,"OFFLINE_SETUP"));
                    tcp.disconnect(oid);
                    try{group.sendGroupText(offlineGroup,"Không nên gửi").toCompletableFuture().get(4,TimeUnit.SECONDS);
                        throw new AssertionError("Disconnected member still sent");}
                    catch(ExecutionException expected){/* owner unreachable */}
                    check(group.groups().stream().noneMatch(g->g.groupName().equals("Reject group")),"Rejected group appeared");
                    System.out.println("PASS_C_RELAY_ACK_REJECT_REMOVE");
                }
            }
            System.out.flush();
            Thread.sleep(200);
        }
    }
}
