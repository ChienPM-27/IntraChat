package vn.intrachat.application;

import model.MessageType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.protocol.WireMessage;
import vn.intrachat.persistence.HistoryRepository;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaP5Test {
    @TempDir Path dir;
    private final UUID a=UUID.randomUUID(), b=UUID.randomUUID(), conversation=UUID.randomUUID();
    private FileTransferService sender,receiver;
    @AfterEach void close(){if(sender!=null)sender.close();if(receiver!=null)receiver.close();}
    private void pair(){
        AtomicReference<FileTransferService> left=new AtomicReference<>(),right=new AtomicReference<>();
        sender=new FileTransferService(a,(peer,type,cid,payload)->
            right.get().handleIncoming(a,new WireMessage(type,UUID.randomUUID(),cid,a,b,Instant.now(),payload)),t->{});
        receiver=new FileTransferService(b,(peer,type,cid,payload)->
            left.get().handleIncoming(b,new WireMessage(type,UUID.randomUUID(),cid,b,a,Instant.now(),payload)),t->{});
        left.set(sender);right.set(receiver);
    }
    private static void await(java.util.function.BooleanSupplier predicate)throws Exception{
        for(int i=0;i<600;i++){if(predicate.getAsBoolean())return;Thread.sleep(10);}
        fail("Media timeout");
    }
    @Test void allMediaUseSameChunksAndPreserveTypeAndIntegrity()throws Exception {
        pair();
        for(MessageType type:List.of(MessageType.FILE,MessageType.IMAGE,MessageType.VIDEO,MessageType.SOUND)){
            byte[] data=new byte[FileTransferService.CHUNK_SIZE*3+31];new Random(type.ordinal()).nextBytes(data);
            Path from=dir.resolve(type+".bin"),to=dir.resolve("received-"+type+".bin");
            Files.write(from,data);
            var offer=sender.offer(b,conversation,from,type);
            assertEquals(type,receiver.get(offer.transferId()).messageType());
            assertEquals(conversation,receiver.get(offer.transferId()).conversationId());
            assertEquals(a,receiver.get(offer.transferId()).senderId());
            assertEquals(offer.sentAt(),sender.get(offer.transferId()).sentAt());
            receiver.accept(offer.transferId(),to);
            await(()->sender.get(offer.transferId()).state()==FileTransferService.State.COMPLETED);
            assertArrayEquals(data,Files.readAllBytes(to));
            assertEquals(type,sender.get(offer.transferId()).messageType());
        }
    }
    @Test void rejectingMediaNeverPublishesOutput()throws Exception{
        pair();
        Path source=dir.resolve("reject.png");Files.write(source,new byte[]{1,2,3});
        var t=sender.offer(b,conversation,source,MessageType.IMAGE);
        receiver.reject(t.transferId());
        assertEquals(FileTransferService.State.REJECTED,sender.get(t.transferId()).state());
        assertEquals(0,sender.get(t.transferId()).transferredBytes());
    }
    @Test void legacyOfferDefaultsToFileAndInvalidTypeFails()throws Exception{
        pair();
        UUID id=UUID.randomUUID();
        receiver.handleIncoming(a,new WireMessage(WireMessage.Type.FILE_OFFER,UUID.randomUUID(),conversation,a,b,
            Instant.now(),Map.of("transferId",id.toString(),"fileName","old.bin","fileSize","17")));
        assertEquals(MessageType.FILE,receiver.get(id).messageType());
        assertThrows(java.io.IOException.class,()->sender.offer(b,conversation,dir.resolve("not-exist"),MessageType.TEXT));
    }
    @Test void historyPersistsMediaTypesAndRestartStates()throws Exception{
        UUID[] ids={UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()};
        try(var repo=new HistoryRepository(dir)){
            int i=0;
            for(String type:List.of("IMAGE","VIDEO","SOUND")){
                UUID id=ids[i++];
                var e=new HistoryRepository.Entry(id,conversation,"DIRECT",a,b,Instant.parse("2026-11-23T05:00:00Z"),
                    type,"","SENDING",id,"media-"+type+".bin",100,"WAITING_ACCEPT","");
                assertTrue(repo.appendMessage(e));
                assertFalse(repo.appendMessage(e));
                repo.updateTransfer(id,"DELIVERED","COMPLETED",dir.resolve(type+".bin").toString());
            }
            repo.flush();
        }
        try(var restored=new HistoryRepository(dir)){
            int i=0;
            for(String type:List.of("IMAGE","VIDEO","SOUND")){
                var entry=restored.find(ids[i++]);
                assertEquals(type,entry.messageType());
                assertEquals("COMPLETED",entry.transferState());
                assertEquals(a,entry.senderId());
                assertEquals(Instant.parse("2026-11-23T05:00:00Z"),entry.sentAtUtc());
                assertEquals(entry.messageId(),entry.transferId());
                assertEquals(1,restored.loadMessages(conversation,500,0).stream().filter(e->e.messageId().equals(entry.messageId())).count());
            }
            assertFalse(Files.readString(restored.file()).contains("base64"));
        }
    }
}
