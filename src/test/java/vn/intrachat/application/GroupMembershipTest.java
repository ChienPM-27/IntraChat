package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.transport.NetworkConfig;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class GroupMembershipTest {
    @TempDir Path temp;

    private static void until(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<deadline){
            if(condition.getAsBoolean())return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for group membership");
    }

    private final class Node implements AutoCloseable {
        final LocalProfile profile;
        final LiveChatService tcp;
        final GroupService groups;
        final LanCoordinator lan;
        Node(String name,GroupService.Invitation invitation)throws Exception{
            profile=LocalProfile.loadOrCreate(temp.resolve(name));
            tcp=new LiveChatService(profile);
            groups=new GroupService(tcp,invitation,Duration.ofSeconds(2));
            lan=new LanCoordinator(profile,info->CompletableFuture.completedFuture(true),peer->{
                try{tcp.attach(peer.session(),peer.name());}catch(Exception e){throw new IllegalStateException(e);}
            });
        }
        UUID id(){return profile.deviceId();}
        @Override public void close(){groups.close();lan.close();tcp.close();}
    }

    @Test void ownerInvitesExistingGroupAndRosterChangesOnlyOnAcceptance()throws Exception{
        BlockingQueue<CompletableFuture<Boolean>> decisions=new LinkedBlockingQueue<>();
        BlockingQueue<GroupService.Event> ownerEvents=new LinkedBlockingQueue<>();
        BlockingQueue<GroupService.Event> inviteeEvents=new LinkedBlockingQueue<>();
        GroupService.Invitation accept=(id,name,group)->CompletableFuture.completedFuture(true);
        try(Node a=new Node("A",accept);Node b=new Node("B",accept);
            Node c=new Node("C",(id,name,group)->{
                var answer=new CompletableFuture<Boolean>();decisions.add(answer);return answer;
            })){
            int port;try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
            a.lan.startListening(new NetworkConfig("127.0.0.1",port));
            b.lan.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            c.lan.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            until(()->a.tcp.connectedPeers().size()==2);
            UUID group=a.groups.createGroup("Existing",List.of(b.id())).toCompletableFuture().get(8,TimeUnit.SECONDS).groupId();
            until(()->b.groups.group(group)!=null&&b.groups.group(group).revision()==1);
            a.groups.addListener(ownerEvents::add);c.groups.addListener(inviteeEvents::add);
            assertThrows(ExecutionException.class,()->b.groups.addMember(group,c.id()).toCompletableFuture().get());
            assertThrows(ExecutionException.class,()->a.groups.addMember(group,b.id()).toCompletableFuture().get());
            assertThrows(ExecutionException.class,()->a.groups.addMember(group,UUID.randomUUID()).toCompletableFuture().get());

            a.groups.addMember(group,c.id()).toCompletableFuture().get(8,TimeUnit.SECONDS);
            var reject=decisions.poll(5,TimeUnit.SECONDS);assertNotNull(reject);
            assertFalse(a.groups.group(group).contains(c.id()));
            assertThrows(ExecutionException.class,()->a.groups.addMember(group,c.id()).toCompletableFuture().get());
            reject.complete(false);
            until(()->ownerEvents.stream().anyMatch(e->e.notice()!=null&&e.notice().contains(c.id().toString())));
            assertEquals(2,a.groups.group(group).memberIds().size());

            a.groups.addMember(group,c.id()).toCompletableFuture().get(8,TimeUnit.SECONDS);
            var late=decisions.poll(5,TimeUnit.SECONDS);assertNotNull(late);
            until(()->!inviteeEvents.isEmpty());
            late.complete(true);
            assertFalse(a.groups.group(group).contains(c.id()));
            assertNull(c.groups.group(group));

            a.groups.addMember(group,c.id()).toCompletableFuture().get(8,TimeUnit.SECONDS);
            var yes=decisions.poll(5,TimeUnit.SECONDS);assertNotNull(yes);yes.complete(true);
            until(()->List.of(a,b,c).stream().allMatch(n->n.groups.group(group)!=null&&n.groups.group(group).memberIds().size()==3));
            assertEquals(2,a.groups.group(group).revision());
            assertEquals(a.groups.group(group),b.groups.group(group));
            assertEquals(a.groups.group(group),c.groups.group(group));
            var message=c.groups.sendGroupText(group,"Hello").toCompletableFuture().get(8,TimeUnit.SECONDS);
            until(()->b.groups.history(group).stream().anyMatch(m->m.messageId().equals(message.messageId())));
            assertEquals(a.groups.group(group),a.tcp.history().groups().stream()
                .filter(g->g.groupId().equals(group)).findFirst().orElseThrow());
        }
    }
}
