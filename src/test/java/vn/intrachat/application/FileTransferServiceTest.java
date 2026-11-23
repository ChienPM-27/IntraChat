package vn.intrachat.application;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.protocol.WireMessage;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class FileTransferServiceTest {
    @TempDir Path dir;
    UUID a=UUID.randomUUID(),b=UUID.randomUUID(),conversation=UUID.randomUUID();
    FileTransferService A,B;
    final AtomicReference<FileTransferService> left=new AtomicReference<>(),right=new AtomicReference<>();
    final List<FileTransferService.Transfer> seenA=new CopyOnWriteArrayList<>(),seenB=new CopyOnWriteArrayList<>();
    void connected(){
        A=new FileTransferService(a,(peer,type,conv,data)->{
            right.get().handleIncoming(a,new WireMessage(type,UUID.randomUUID(),conv,a,b,Instant.now(),data));
        },seenA::add);
        B=new FileTransferService(b,(peer,type,conv,data)->{
            left.get().handleIncoming(b,new WireMessage(type,UUID.randomUUID(),conv,b,a,Instant.now(),data));
        },seenB::add);
        left.set(A);right.set(B);
    }
    @AfterEach void close(){if(A!=null)A.close();if(B!=null)B.close();}
    static void until(java.util.function.BooleanSupplier pred)throws Exception{
        for(int i=0;i<400;i++){if(pred.getAsBoolean())return;Thread.sleep(15);}
        fail("Timed out waiting for file state");
    }
    Path binary(String filename,int size)throws Exception{
        byte[] contents=new byte[size];new Random(123).nextBytes(contents);
        Path file=dir.resolve(filename);Files.write(file,contents);return file;
    }
    @Test void realBinaryMultipleChunksAndUnicode()throws Exception{
        connected();
        Path src=binary("Bản thử dữ liệu 🇻🇳.bin",FileTransferService.CHUNK_SIZE*6+73);
        var offered=A.offer(b,conversation,src);
        var received=seenB.get(0);
        assertEquals(src.getFileName().toString(),received.fileName());
        Path dest=dir.resolve("destination.bin");
        B.accept(offered.transferId(),dest);
        until(()->A.get(offered.transferId()).state()==FileTransferService.State.COMPLETED);
        assertArrayEquals(Files.readAllBytes(src),Files.readAllBytes(dest));
        assertEquals(FileTransferService.State.COMPLETED,B.get(offered.transferId()).state());
        assertEquals(Files.size(src),A.get(offered.transferId()).transferredBytes());
        assertTrue(seenA.stream().anyMatch(t->t.state()==FileTransferService.State.TRANSFERRING));
    }
    @Test void emptyFileCompletesAfterReceiverAck()throws Exception{
        connected();
        Path src=binary("empty.bin",0),target=dir.resolve("empty-received.bin");
        var t=A.offer(b,conversation,src);
        B.accept(t.transferId(),target);
        until(()->A.get(t.transferId()).state()==FileTransferService.State.COMPLETED);
        assertEquals(0,Files.size(target));
    }
    @Test void rejectWithoutOutput()throws Exception{
        connected();var t=A.offer(b,conversation,binary("reject.bin",1024));
        B.reject(t.transferId());
        assertEquals(FileTransferService.State.REJECTED,A.get(t.transferId()).state());
        assertEquals(0,A.get(t.transferId()).transferredBytes());
    }
    @Test void senderCancelBeforeAcceptAndBusyLimit()throws Exception{
        connected();var t=A.offer(b,conversation,binary("cancel.bin",512));
        assertThrows(IOException.class,()->A.offer(b,conversation,binary("other.bin",128)));
        A.cancel(t.transferId());
        assertEquals(FileTransferService.State.CANCELLED,B.get(t.transferId()).state());
        B.cancel(t.transferId());
        assertEquals(FileTransferService.State.CANCELLED,A.get(t.transferId()).state());
    }
    @Test void existingDestinationCannotBeOverwritten()throws Exception{
        connected();var t=A.offer(b,conversation,binary("test.bin",512));
        Path dest=binary("old.bin",15);byte[] before=Files.readAllBytes(dest);
        assertThrows(FileAlreadyExistsException.class,()->B.accept(t.transferId(),dest));
        assertArrayEquals(before,Files.readAllBytes(dest));
        B.reject(t.transferId());
    }
    @Test void suspiciousNamesRejected() {
        for(String name:List.of("../a","a\\b",":drive","..","file.","bad\nname","","CON.txt","LPT1","a?b","a|b"))
            assertThrows(IllegalArgumentException.class,()->FileTransferService.safeFileName(name));
        assertEquals("Tiếng Việt.txt",FileTransferService.safeFileName("Tiếng Việt.txt"));
    }
    @Test void missingSourceFails()throws Exception{
        connected();
        assertThrows(IOException.class,()->A.offer(b,conversation,dir.resolve("missing.bin")));
    }
    @Test void invalidAndDuplicateChunksFailAndCleanTemp()throws Exception{
        connected();var t=A.offer(b,conversation,binary("test.bin",1024));
        Path dest=dir.resolve("never.bin");
        // Prevent normal sending: inject a chunk before accept (invalid state)
        B.handleIncoming(a,new WireMessage(WireMessage.Type.FILE_CHUNK,UUID.randomUUID(),conversation,
            a,b,Instant.now(),Map.of("transferId",t.transferId().toString(),"offset","0","data","AA==")));
        assertEquals(FileTransferService.State.FAILED,B.get(t.transferId()).state());
        assertFalse(Files.exists(dest));
    }
    @Test void disconnectedDuringOfferFails()throws Exception{
        connected();var t=A.offer(b,conversation,binary("test.bin",50));
        A.disconnected(b);B.disconnected(a);
        assertEquals(FileTransferService.State.FAILED,A.get(t.transferId()).state());
        assertEquals(FileTransferService.State.FAILED,B.get(t.transferId()).state());
    }
    @Test void crcMismatchDoesNotPublishFile()throws Exception{
        B=new FileTransferService(b,(peer,type,conv,payload)->{},seenB::add);
        UUID id=UUID.randomUUID();
        Path dest=dir.resolve("invalid.bin");
        B.handleIncoming(a,new WireMessage(WireMessage.Type.FILE_OFFER,UUID.randomUUID(),conversation,
            a,b,Instant.now(),Map.of("transferId",id.toString(),"fileName","safe.bin","fileSize","3")));
        B.accept(id,dest);
        B.handleIncoming(a,new WireMessage(WireMessage.Type.FILE_CHUNK,UUID.randomUUID(),conversation,
            a,b,Instant.now(),Map.of("transferId",id.toString(),"offset","0","data","AQID")));
        B.handleIncoming(a,new WireMessage(WireMessage.Type.FILE_DONE,UUID.randomUUID(),conversation,
            a,b,Instant.now(),Map.of("transferId",id.toString(),"size","3","crc32","0")));
        assertEquals(FileTransferService.State.FAILED,B.get(id).state());
        assertFalse(Files.exists(dest));
        try(var parts=Files.list(dir)){assertEquals(0,parts.filter(f->f.getFileName().toString().endsWith(".part")).count());}
    }
}
