package vn.intrachat.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.ChatGroup;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class HistoryRepositoryTest {
    @TempDir Path dir;
    UUID me=UUID.randomUUID(),peer=UUID.randomUUID(),conversation=UUID.randomUUID();
    Instant when=Instant.parse("2026-11-23T01:02:03.123Z");
    HistoryRepository.Entry entry(UUID id,String text){
        return new HistoryRepository.Entry(id,conversation,"DIRECT",me,peer,when,
            "TEXT",text,"SENDING",null,"",0,"","");
    }
    @Test void textUnicodeEmojiAndOriginalMetadata() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){assertTrue(repo.appendMessage(entry(id,"Tiếng Việt 👨‍👩‍👧 😀")));repo.flush();}
        try(var repo=new HistoryRepository(dir)){
            var saved=repo.loadMessages(conversation,20,0).getFirst();
            assertEquals(id,saved.messageId());assertEquals(me,saved.senderId());assertEquals(peer,saved.recipientId());
            assertEquals(when,saved.sentAtUtc());assertEquals("Tiếng Việt 👨‍👩‍👧 😀",saved.text());
            assertEquals("DIRECT",saved.conversationType());
        }
    }
    @Test void noDuplicateWhenAppendingSameMessage() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            assertTrue(repo.appendMessage(entry(id,"one")));
            assertFalse(repo.appendMessage(entry(id,"one")));
            repo.updateState(id,"LOCAL_SENT");
            repo.updateState(id,"APP_ACKNOWLEDGED");
        }
        try(var repo=new HistoryRepository(dir)){
            assertEquals(1,repo.loadMessages(conversation,20,0).size());
            assertEquals("APP_ACKNOWLEDGED",repo.find(id).deliveryState());
        }
    }
    @Test void conflictingIdIsRejected() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.appendMessage(entry(id,"one"));
            assertThrows(IllegalArgumentException.class,()->repo.appendMessage(new HistoryRepository.Entry(
                id,UUID.randomUUID(),"DIRECT",me,peer,when,"TEXT","two","SENT",null,"",0,"","")));
        }
    }
    @Test void seenNeverRegressesToSent() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.appendMessage(entry(id,"hello"));
            repo.updateState(id,"LOCAL_SENT");repo.updateState(id,"APP_ACKNOWLEDGED");
            repo.updateState(id,"READ");repo.updateState(id,"LOCAL_SENT");
            assertEquals("READ",repo.find(id).deliveryState());
        }
        try(var repo=new HistoryRepository(dir)){assertEquals("READ",repo.find(id).deliveryState());}
    }
    @Test void failedNeverBecomesDelivered() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.appendMessage(entry(id,"hello"));repo.updateState(id,"FAILED");
            repo.updateState(id,"APP_ACKNOWLEDGED");
        }
        try(var repo=new HistoryRepository(dir)){assertEquals("FAILED",repo.find(id).deliveryState());}
    }
    @Test void chronologicalOrderWithEqualTimestampsUsesMessageId() throws Exception {
        UUID a=UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b=UUID.fromString("00000000-0000-0000-0000-000000000002");
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(entry(b,"B"));repo.appendMessage(entry(a,"A"));}
        try(var repo=new HistoryRepository(dir)){
            var all=repo.loadMessages(conversation,20,0);
            assertEquals(List.of(a,b),all.stream().map(HistoryRepository.Entry::messageId).toList());
        }
    }
    @Test void limitAndOlderPage() throws Exception {
        try(var repo=new HistoryRepository(dir)){
            for(int i=0;i<25;i++){
                var e=entry(UUID.randomUUID(),"text"+i);
                repo.appendMessage(new HistoryRepository.Entry(e.messageId(),conversation,"DIRECT",me,peer,
                    when.plusSeconds(i),"TEXT",e.text(),"SENT",null,"",0,"",""));
            }
        }
        try(var repo=new HistoryRepository(dir)){
            assertEquals("text24",repo.loadMessages(conversation,5,0).getLast().text());
            assertEquals("text19",repo.loadMessages(conversation,5,5).getLast().text());
            assertEquals(25,repo.loadMessages(conversation,1000,0).size());
        }
    }
    @Test void groupMetadataAndRosterSurviveRestart() throws Exception {
        var group=new ChatGroup(UUID.randomUUID(),"Nhóm ABC",me,List.of(me,peer),4);
        try(var repo=new HistoryRepository(dir)){repo.saveGroup(group);}
        try(var repo=new HistoryRepository(dir)){
            assertEquals(group,repo.groups().getFirst());
            assertTrue(repo.conversations().contains(group.groupId()));
        }
    }
    @Test void removedMembershipDoesNotReturn() throws Exception {
        var gid=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.saveGroup(new ChatGroup(gid,"N",me,List.of(me,peer),1));
            repo.saveGroup(new ChatGroup(gid,"N",me,List.of(me),2));
        }
        try(var repo=new HistoryRepository(dir)){
            assertFalse(repo.groups().getFirst().contains(peer));
            assertEquals(2,repo.groups().getFirst().revision());
        }
    }
    @Test void peerAliasRestoredIndependentlyOfOnlineStatus() throws Exception {
        try(var repo=new HistoryRepository(dir)){repo.savePeer(peer,"Bạn B");}
        try(var repo=new HistoryRepository(dir)){assertEquals("Bạn B",repo.peers().get(peer));}
    }
    @Test void fileMetadataNotFileBytes() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.appendMessage(new HistoryRepository.Entry(id,conversation,"DIRECT",me,peer,when,
                "FILE","","SENDING",id,"picture.png",1024,"OFFERED",""));
            repo.updateTransfer(id,"DELIVERED","COMPLETED",dir.resolve("picture.png").toString());
        }
        String json=Files.readString(dir.resolve("history/messages.jsonl"));
        assertTrue(json.contains("picture.png"));assertFalse(json.contains("dataBase64"));
        try(var repo=new HistoryRepository(dir)){
            assertEquals("COMPLETED",repo.find(id).transferState());
            assertEquals(1024,repo.find(id).fileSize());
        }
    }
    @Test void terminalTransferCannotRegressAfterRestart() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){
            repo.appendMessage(new HistoryRepository.Entry(id,conversation,"DIRECT",me,peer,when,
                "FILE","","SENDING",id,"report.txt",100,"OFFERED",""));
            repo.updateTransfer(id,"DELIVERED","COMPLETED","report.txt");
            repo.updateTransfer(id,"DELIVERED","CANCELLED","");
            assertEquals("COMPLETED",repo.find(id).transferState());
        }
        try(var repo=new HistoryRepository(dir)){
            repo.updateTransfer(id,"DELIVERED","FAILED","");
            assertEquals("COMPLETED",repo.find(id).transferState());
        }
    }
    @Test void interruptedFileRestoresAsInterrupted() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(new HistoryRepository.Entry(id,conversation,
            "DIRECT",me,peer,when,"FILE","","SENDING",id,"draft.zip",10400,"TRANSFERRING",""));}
        try(var repo=new HistoryRepository(dir)){
            assertEquals("INTERRUPTED",repo.find(id).transferState());
            assertEquals("FAILED",repo.find(id).deliveryState());
        }
    }
    @Test void fileMissingAndEmpty() throws Exception {
        try(var repo=new HistoryRepository(dir)){assertTrue(repo.loadMessages(conversation,20,0).isEmpty());}
        Files.writeString(dir.resolve("history/messages.jsonl"),"");
        try(var repo=new HistoryRepository(dir)){assertTrue(repo.conversations().isEmpty());}
    }
    @Test void corruptRowsDoNotHideValidRows() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(entry(id,"valid"));repo.flush();}
        Files.writeString(dir.resolve("history/messages.jsonl"),"{broken}\n",StandardOpenOption.APPEND);
        try(var repo=new HistoryRepository(dir)){assertNotNull(repo.find(id));}
    }
    @Test void truncatedLastRowDoesNotBreakNextAppend() throws Exception {
        UUID id=UUID.randomUUID(),next=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(entry(id,"before"));repo.flush();}
        Files.writeString(dir.resolve("history/messages.jsonl"),"{broken",StandardOpenOption.APPEND);
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(entry(next,"after"));}
        try(var repo=new HistoryRepository(dir)){assertNotNull(repo.find(id));assertNotNull(repo.find(next));}
    }
    @Test void invalidUuidRowIsSkipped() throws Exception {
        UUID id=UUID.randomUUID();
        try(var repo=new HistoryRepository(dir)){repo.appendMessage(entry(id,"valid"));}
        Files.writeString(dir.resolve("history/messages.jsonl"),
            "{\"event\":\"STATE\",\"messageId\":\"oops\",\"deliveryState\":\"READ\"}\n",StandardOpenOption.APPEND);
        try(var repo=new HistoryRepository(dir)){assertEquals("SENDING",repo.find(id).deliveryState());}
    }
    @Test void separateDataDirsNeverMix() throws Exception {
        Path a=dir.resolve("A"),b=dir.resolve("B");
        UUID id=UUID.randomUUID();
        try(var first=new HistoryRepository(a);var second=new HistoryRepository(b)){
            first.appendMessage(entry(id,"private"));
            first.flush();assertNull(second.find(id));
        }
        try(var second=new HistoryRepository(b)){assertNull(second.find(id));}
    }
    @Test void closeFlushesAndWriterTerminates() throws Exception {
        UUID id=UUID.randomUUID();
        var repo=new HistoryRepository(dir);repo.appendMessage(entry(id,"flush"));repo.close();
        assertThrows(IllegalStateException.class,()->repo.appendMessage(entry(UUID.randomUUID(),"later")));
        try(var again=new HistoryRepository(dir)){assertNotNull(again.find(id));}
    }
    @Test void concurrentAppendProducesValidJsonLines() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try(var repo=new HistoryRepository(dir)){
            List<Future<?>> jobs=new ArrayList<>();
            for(int i=0;i<100;i++){
                final int n=i;jobs.add(pool.submit(()->repo.appendMessage(entry(UUID.randomUUID(),"text"+n))));
            }
            for(var job:jobs)job.get(5,TimeUnit.SECONDS);
            repo.flush();assertEquals(100,repo.loadMessages(conversation,500,0).size());
        }finally{pool.shutdownNow();}
        try(var repo=new HistoryRepository(dir)){assertEquals(100,repo.loadMessages(conversation,500,0).size());}
    }
    @Test void doNotBlockSwingEdtWritingHistory() throws Exception {
        var repo=new HistoryRepository(dir);
        try{
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->{
                javax.swing.SwingUtilities.invokeAndWait(()->repo.appendMessage(entry(UUID.randomUUID(),"EDT")));
            });
        }finally{repo.close();}
    }
}
