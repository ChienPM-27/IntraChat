package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import vn.intrachat.transport.LanSession;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GroupInvitationTimeoutTest {
    @Test
    void unansweredInviteTimesOutAndLateAcceptanceCannotJoin() throws Exception {
        LocalProfile a = LocalProfile.loadOrCreate(Files.createTempDirectory("p2-timeout-a"));
        LocalProfile b = LocalProfile.loadOrCreate(Files.createTempDirectory("p2-timeout-b"));
        CompletableFuture<Boolean> lateDecision = new CompletableFuture<>();
        try (ServerSocket listener = new ServerSocket(0);
             Socket client = new Socket("127.0.0.1", listener.getLocalPort());
             Socket server = listener.accept();
             LiveChatService owner = new LiveChatService(a);
             LiveChatService invitee = new LiveChatService(b);
             GroupService ownerGroups = new GroupService(owner,
                 (id, name, group) -> CompletableFuture.completedFuture(true), Duration.ofMillis(250));
             GroupService inviteeGroups = new GroupService(invitee,
                 (id, name, group) -> lateDecision)) {
            owner.attach(new LanSession(server, a.deviceId(), b.deviceId()), b.displayName());
            invitee.attach(new LanSession(client, b.deviceId(), a.deviceId()), a.displayName());
            var creation = ownerGroups.createGroup("No reply", List.of(b.deviceId())).toCompletableFuture();
            ExecutionException error = assertThrows(ExecutionException.class,
                () -> creation.get(5, TimeUnit.SECONDS));
            assertTrue(error.getCause().getMessage().contains("timed out"));
            assertTrue(ownerGroups.groups().isEmpty());
            // Expiration sends GROUP_REMOVE to dismiss the outstanding invite.
            Thread.sleep(250);
            lateDecision.complete(true);
            Thread.sleep(100);
            assertTrue(inviteeGroups.groups().isEmpty(), "Expired invitation must not join");
        }
    }

    @Test
    void invalidTimeoutIsRejected() throws Exception {
        LocalProfile a = LocalProfile.loadOrCreate(Files.createTempDirectory("p2-timeout-invalid"));
        try (LiveChatService tcp = new LiveChatService(a)) {
            assertThrows(IllegalArgumentException.class,
                () -> new GroupService(tcp, (id, name, group) ->
                    CompletableFuture.completedFuture(true), Duration.ZERO));
        }
    }
}
