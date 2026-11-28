package vn.intrachat.application;

import model.MessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.P2PConversation;
import vn.intrachat.transport.NetworkConfig;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AutoSaveMediaTest {
    @TempDir Path temp;

    @Test
    void incomingMediaAutoSavesIntoRecipientUserMediaDirWithoutPrompt() throws Exception {
        Path dataRoot = temp.resolve("data");
        LocalProfile alice = LocalProfile.loadOrCreate(dataRoot.resolve("Alice"), "Alice");
        LocalProfile bob = LocalProfile.loadOrCreate(dataRoot.resolve("Bob"), "Bob");

        int port;
        try (ServerSocket reserve = new ServerSocket(0)) {
            port = reserve.getLocalPort();
        }

        try (LiveChatService sa = new LiveChatService(alice);
             LiveChatService sb = new LiveChatService(bob);
             LanCoordinator ca = new LanCoordinator(alice, req -> CompletableFuture.completedFuture(true),
                 peer -> { try { sa.attach(peer.session(), peer.name()); } catch (Exception ex) { throw new RuntimeException(ex); } });
             LanCoordinator cb = new LanCoordinator(bob, req -> CompletableFuture.completedFuture(true),
                 peer -> { try { sb.attach(peer.session(), peer.name()); } catch (Exception ex) { throw new RuntimeException(ex); } })) {
            ca.startListening(new NetworkConfig("127.0.0.1", port));
            cb.connect(new NetworkConfig("127.0.0.1", port)).get(8, TimeUnit.SECONDS);

            Path sourceDir = temp.resolve("outbound");
            Files.createDirectories(sourceDir);
            Path sourceFile = sourceDir.resolve("sample.txt");
            byte[] content = "hello-intrachat-media".getBytes(StandardCharsets.UTF_8);
            Files.write(sourceFile, content);

            UUID convId = P2PConversation.idFor(alice.deviceId(), bob.deviceId());
            FileTransferService.Transfer first = sa.files().offer(bob.deviceId(), convId, sourceFile, MessageType.FILE);
            awaitCompleted(sa.files(), first.transferId());
            awaitCompleted(sb.files(), first.transferId());

            Path bobSaved1 = bob.mediaDir().resolve("sample.txt");
            assertTrue(Files.isRegularFile(bobSaved1));
            assertArrayEquals(content, Files.readAllBytes(bobSaved1));
            assertFalse(Files.exists(alice.mediaDir().resolve("sample.txt")));

            // Sending the same filename again saves as "sample (1).txt" without overwriting or failing.
            FileTransferService.Transfer second = sa.files().offer(bob.deviceId(), convId, sourceFile, MessageType.FILE);
            awaitCompleted(sa.files(), second.transferId());
            awaitCompleted(sb.files(), second.transferId());

            Path bobSaved2 = bob.mediaDir().resolve("sample (1).txt");
            assertTrue(Files.isRegularFile(bobSaved2));
            assertArrayEquals(content, Files.readAllBytes(bobSaved2));
        }
    }

    @Test
    void destinationCreationFailureNotifiesSenderWithoutWaitingForTimeout() throws Exception {
        LocalProfile a=LocalProfile.loadOrCreate(temp.resolve("blocked-A"));
        LocalProfile b=LocalProfile.loadOrCreate(temp.resolve("blocked-B"));
        Files.write(b.mediaDir(),new byte[]{1}); // the media directory cannot be created
        int port;try(ServerSocket reserve=new ServerSocket(0)){port=reserve.getLocalPort();}
        try(var sa=new LiveChatService(a);var sb=new LiveChatService(b);
            var ca=new LanCoordinator(a,r->CompletableFuture.completedFuture(true),p->{try{sa.attach(p.session(),p.name());}catch(Exception e){throw new RuntimeException(e);}});
            var cb=new LanCoordinator(b,r->CompletableFuture.completedFuture(true),p->{try{sb.attach(p.session(),p.name());}catch(Exception e){throw new RuntimeException(e);}})){
            ca.startListening(new NetworkConfig("127.0.0.1",port));
            cb.connect(new NetworkConfig("127.0.0.1",port)).get(8,TimeUnit.SECONDS);
            Path source=temp.resolve("blocked.bin");Files.write(source,new byte[]{1,2});
            var sent=sa.files().offer(b.deviceId(),sa.conversationId(b.deviceId()),source,MessageType.FILE);
            long deadline=System.currentTimeMillis()+5000;
            while(System.currentTimeMillis()<deadline&&sa.files().get(sent.transferId()).state()!=FileTransferService.State.FAILED)Thread.sleep(20);
            assertEquals(FileTransferService.State.FAILED,sa.files().get(sent.transferId()).state());
            assertEquals(FileTransferService.State.FAILED,sb.files().get(sent.transferId()).state());
            assertTrue(sb.files().get(sent.transferId()).error().contains("Không thể tự lưu"));
            assertArrayEquals(new byte[]{1},Files.readAllBytes(b.mediaDir()));
        }
    }

    private static void awaitCompleted(FileTransferService files, UUID id) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            var t = files.get(id);
            if (t != null && t.state() == FileTransferService.State.COMPLETED) return;
            Thread.sleep(20);
        }
        fail("Transfer did not complete: " + files.get(id));
    }
}
