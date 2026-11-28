package vn.intrachat.application;

import model.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.ChatGroup;
import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.transport.NetworkConfig;
import java.net.ServerSocket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class GroupMediaEndToEndTest {
    @TempDir Path temp;
    private static void until(BooleanSupplier check) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(System.nanoTime()<deadline){if(check.getAsBoolean())return;Thread.sleep(20);}
        fail("Timed out waiting for group media");
    }
    private final class Node implements AutoCloseable {
        final LocalProfile profile;
        final LiveChatService tcp;
        final GroupService groups;
        final LanCoordinator lan;
        final Map<UUID,GroupService.MediaEvent> events=new ConcurrentHashMap<>();
        final Map<UUID,FileTransferService.Transfer> files=new ConcurrentHashMap<>();
        Node(String name)throws Exception{
            profile=LocalProfile.loadOrCreate(temp.resolve(name));
            tcp=new LiveChatService(profile);
            tcp.enableAutoReceive(temp.resolve("received-files"));
            groups=new GroupService(tcp,(owner,alias,title)->CompletableFuture.completedFuture(true));
            groups.addMediaListener(e->events.put(e.transfer().transferId(),e));
            tcp.addFileListener(t->{if(t.groupRef()==null)files.put(t.transferId(),t);});
            lan=new LanCoordinator(profile,info->CompletableFuture.completedFuture(true),peer->{
                try{tcp.attach(peer.session(),peer.name());}catch(Exception e){throw new IllegalStateException(e);}
            });
        }
        UUID id(){return profile.deviceId();}
        @Override public void close(){groups.close();lan.close();tcp.close();}
    }
    private void verify(Node node,FileTransferService.Transfer original,Path source)throws Exception{
        UUID id=original.transferId();
        until(()->node.events.containsKey(id)&&node.events.get(id).transfer().state()==FileTransferService.State.COMPLETED);
        var row=node.events.get(id).transfer();
        assertEquals(original.senderId(),row.senderId());
        assertEquals(original.sentAt(),row.sentAt());
        assertEquals(original.conversationId(),row.conversationId());
        assertEquals(original.messageType(),row.messageType());
        assertTrue(row.localPath().startsWith(temp.resolve("received-files").resolve(node.id().toString())));
        assertEquals(source.getFileName(),row.localPath().getFileName());
        assertArrayEquals(Files.readAllBytes(source),Files.readAllBytes(row.localPath()));
        var history=node.tcp.history().loadMessages(original.conversationId(),500,0);
        assertEquals(1,history.stream().filter(e->e.messageId().equals(id)).count());
        assertEquals("GROUP",node.tcp.history().find(id).conversationType());
        assertEquals("COMPLETED",node.tcp.history().find(id).transferState());
    }
    @Test void ownerAndMemberRelayAllTypesWithStableIdentityAutomaticSaveAndPartialFailure()throws Exception{
        Path sourceDir=Files.createDirectories(temp.resolve("sources"));
        try(Node a=new Node("A");Node b=new Node("B");Node c=new Node("C")){
            int port;try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
            a.lan.startListening(new NetworkConfig("127.0.0.1",port));
            b.lan.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            c.lan.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            ChatGroup created=a.groups.createGroup("Media",List.of(b.id(),c.id())).toCompletableFuture().get(8,TimeUnit.SECONDS);
            UUID group=created.groupId();
            until(()->List.of(a,b,c).stream().allMatch(n->n.groups.group(group)!=null&&n.groups.group(group).memberIds().size()==3));
            for(MessageType type:FileTransferService.MEDIA_TYPES){
                Path src=sourceDir.resolve(type+".bin");
                byte[] bytes=new byte[FileTransferService.CHUNK_SIZE*3+17];new Random(type.ordinal()).nextBytes(bytes);Files.write(src,bytes);
                // Send from both owner and member, same filename: no overwrite or duplicate logical message.
                var owner=a.groups.sendGroupMedia(group,src,type);
                verify(b,owner,src);verify(c,owner,src);
                until(()->a.events.get(owner.transferId()).transfer().state()==FileTransferService.State.COMPLETED);
                assertEquals(2,a.events.get(owner.transferId()).received());
                var member=b.groups.sendGroupMedia(group,src,type);
                verify(a,member,src);verify(c,member,src);
                until(()->b.events.get(member.transferId()).transfer().state()==FileTransferService.State.COMPLETED);
                assertEquals(2,b.events.get(member.transferId()).received());
                assertNotEquals(c.events.get(owner.transferId()).transfer().localPath(),c.events.get(member.transferId()).transfer().localPath());
            }
            // No direct B-C connection exists; all member media must traverse A.
            assertFalse(b.tcp.connectedPeers().containsKey(c.id()));
            // Direct media also auto-saves into the same isolated root.
            Path direct=sourceDir.resolve("direct.bin");Files.write(direct,new byte[]{4,3,2,1});
            var p2p=b.tcp.files().offer(a.id(),b.tcp.conversationId(a.id()),direct,MessageType.FILE);
            until(()->a.files.containsKey(p2p.transferId())&&a.files.get(p2p.transferId()).state()==FileTransferService.State.COMPLETED
                &&b.files.get(p2p.transferId()).state()==FileTransferService.State.COMPLETED);
            assertArrayEquals(Files.readAllBytes(direct),Files.readAllBytes(a.files.get(p2p.transferId()).localPath()));
            assertTrue(a.files.get(p2p.transferId()).localPath().startsWith(temp.resolve("received-files")));
            // A cannot claim all members received if C is offline.
            a.lan.disconnect(c.id());
            until(()->!a.tcp.connectedPeers().containsKey(c.id()));
            var partial=b.groups.sendGroupMedia(group,direct,MessageType.FILE);
            verify(a,partial,direct);
            until(()->b.events.get(partial.transferId()).transfer().state()==FileTransferService.State.FAILED);
            assertEquals(1,b.events.get(partial.transferId()).received());
            assertEquals(1,b.events.get(partial.transferId()).failed());
            assertEquals("FAILED",b.tcp.history().find(partial.transferId()).deliveryState());
            assertNull(c.tcp.history().find(partial.transferId()));
            a.tcp.history().flush();b.tcp.history().flush();c.tcp.history().flush();
        }
        try(var restored=new HistoryRepository(temp.resolve("C"))){
            var rows=restored.conversations().stream().flatMap(id->restored.loadMessages(id,500,0).stream())
                .filter(e->e.conversationType().equals("GROUP")&&HistoryRepository.isFileType(e.messageType())).toList();
            assertEquals(8,rows.size());
            assertEquals(8,rows.stream().map(HistoryRepository.Entry::messageId).distinct().count());
            assertTrue(rows.stream().allMatch(e->e.transferState().equals("COMPLETED")&&Files.isRegularFile(Path.of(e.localPath()))));
        }
    }
}
