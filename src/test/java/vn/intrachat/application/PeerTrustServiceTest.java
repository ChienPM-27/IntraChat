package vn.intrachat.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.domain.VerifiedPeerIdentity;
import vn.intrachat.domain.P2PConversation;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security boundary: these frames represent messages already received over a
 * matching mutual TLS link, never raw bootstrap data. A remote claim is not proof.
 */
class PeerTrustServiceTest {
    @TempDir Path root;
    private static DeviceIdentity newIdentity(){
        try{return DeviceCertificateFactory.create(UUID.randomUUID());}
        catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}
    }
    private final DeviceIdentity local=newIdentity();
    private final DeviceIdentity remote=newIdentity();
    private final UUID requestId=UUID.randomUUID();
    private final AtomicLong tick=new AtomicLong();
    private final byte[] ownNonce=new byte[32],remoteNonce=new byte[32];
    private final Instant sentAt=Instant.parse("2026-11-22T07:00:00Z");

    private PeerTrustService create(Path file){
        return new PeerTrustService(local,remote.deviceId(),remote.fingerprint(),
            ownNonce,remoteNonce,requestId,new PinnedPeerStore(file),
            new PairingStateMachine(requestId),tick::get,Duration.ofSeconds(120));
    }
    private ChatV2Message proof(PeerTrustService trust){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_CONFIRM,requestId,
            remote.deviceId(),local.deviceId(),trust.conversationId(),sentAt,
            Map.of("requestId",requestId.toString(),"transcriptHash",trust.transcriptHex(),
                "confirmationId",UUID.randomUUID().toString()));
    }
    private ChatV2Message ready(PeerTrustService trust){
        return new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_READY,requestId,
            remote.deviceId(),local.deviceId(),trust.conversationId(),sentAt,
            Map.of("requestId",requestId.toString(),"transcriptHash",trust.transcriptHex()));
    }

    @Test void firstPairNeedsHumanAndBothProtectedConfirmationsThenPins()throws Exception{
        Path pin=root.resolve("first.pin");
        var trust=create(pin);
        assertFalse(trust.isAlreadyPinned());
        assertFalse(trust.canChat());
        assertThrows(IllegalStateException.class,()->trust.acceptPeerConfirmation(proof(trust)));
        trust.approveLocalComparison(true);
        assertFalse(trust.canChat());
        assertFalse(Files.exists(pin),"One-sided human confirmation cannot create trust");
        var confirmed=proof(trust);
        trust.acceptPeerConfirmation(confirmed);
        assertThrows(IllegalStateException.class,()->trust.acceptPeerConfirmation(confirmed),
            "Protected PAIR_CONFIRM cannot replay within one session");
        assertFalse(Files.exists(pin),"Partial confirmation cannot silently pin");
        trust.markLocalReady();
        assertFalse(trust.canChat(),"Our READY alone is not a protected session");
        VerifiedPeerIdentity identity=trust.acceptPeerReadyAndCommit(ready(trust));
        assertTrue(trust.canChat());
        assertEquals(remote.deviceId(),identity.deviceId());
        assertEquals(requestId,identity.requestId());
        assertEquals(trust.conversationId(),identity.conversationId());
        assertTrue(new PinnedPeerStore(pin).matches(remote.deviceId(),remote.fingerprint()));
        assertThrows(IllegalStateException.class,()->trust.acceptPeerReadyAndCommit(ready(trust)));
        byte[] leaked=identity.spkiSha256();
        Arrays.fill(leaked,(byte)0);
        assertArrayEquals(remote.fingerprint(),identity.spkiSha256(),
            "Verified identity fingerprint must be immutable");
    }

    @Test void rejectTimeoutOrPartialCommitNeverMakesVerified()throws Exception{
        var rejected=create(root.resolve("denied.pin"));
        assertThrows(IllegalStateException.class,()->rejected.approveLocalComparison(false));
        assertFalse(rejected.canChat());
        assertThrows(IllegalStateException.class,()->rejected.markLocalReady());

        var expired=create(root.resolve("expired.pin"));
        expired.approveLocalComparison(true);
        tick.addAndGet(Duration.ofSeconds(121).toNanos());
        assertThrows(IllegalStateException.class,()->expired.acceptPeerConfirmation(proof(expired)));
        assertFalse(expired.canChat());

        tick.set(0);
        Path unreadable=root.resolve("occupied.pin");
        var impossible=create(unreadable);
        impossible.approveLocalComparison(true);
        impossible.acceptPeerConfirmation(proof(impossible));
        impossible.markLocalReady();
        Files.createDirectory(unreadable); // tampered just before trust commit
        assertThrows(java.io.IOException.class,
            ()->impossible.acceptPeerReadyAndCommit(ready(impossible)));
        assertFalse(impossible.canChat(),"Failed protected pin persistence cannot authorize user CHAT");
    }

    @Test void wrongRequestNonceSenderConversationAndTranscriptCannotConfirm()throws Exception{
        var trust=create(root.resolve("binding.pin"));
        trust.approveLocalComparison(true);
        var good=proof(trust);
        var payload=good.payload();
        assertThrows(IllegalArgumentException.class,()->trust.acceptPeerConfirmation(
            new ChatV2Message(good.messageId(),good.type(),UUID.randomUUID(),
                good.senderId(),good.destinationId(),good.conversationId(),good.sentAt(),payload)));
        assertThrows(IllegalStateException.class,()->trust.acceptPeerConfirmation(good),
            "Wrong first session must poison handshake, not permit later valid proof");
        var wrongSender=create(root.resolve("wrong-sender.pin"));
        wrongSender.approveLocalComparison(true);
        assertThrows(IllegalArgumentException.class,()->wrongSender.acceptPeerConfirmation(
            new ChatV2Message(good.messageId(),good.type(),requestId,
                UUID.randomUUID(),good.destinationId(),good.conversationId(),good.sentAt(),payload)));
        var wrongConversation=create(root.resolve("wrong-conversation.pin"));
        wrongConversation.approveLocalComparison(true);
        assertThrows(IllegalArgumentException.class,()->wrongConversation.acceptPeerConfirmation(
            new ChatV2Message(good.messageId(),good.type(),requestId,
                good.senderId(),good.destinationId(),UUID.randomUUID(),good.sentAt(),payload)));
        var wrong=new HashMap<>(payload);
        wrong.put("transcriptHash","0".repeat(64));
        var wrongTranscript=create(root.resolve("wrong-transcript.pin"));
        wrongTranscript.approveLocalComparison(true);
        assertThrows(IllegalArgumentException.class,()->wrongTranscript.acceptPeerConfirmation(
            new ChatV2Message(good.messageId(),good.type(),requestId,
                good.senderId(),good.destinationId(),good.conversationId(),good.sentAt(),wrong)));
        assertFalse(trust.canChat());
        var changedOwn=ownNonce.clone();changedOwn[0]=1;
        var changed=new PeerTrustService(local,remote.deviceId(),remote.fingerprint(),
            changedOwn,remoteNonce,requestId,new PinnedPeerStore(root.resolve("new.pin")),
            new PairingStateMachine(requestId),tick::get,Duration.ofSeconds(120));
        assertNotEquals(changed.transcriptHex(),trust.transcriptHex());
    }

    @Test void revokeDuringPairMustNotBeUndoneByLateReady()throws Exception{
        Path file=root.resolve("revoke-in-flight.pin");
        PinnedPeerStore pins=new PinnedPeerStore(file);
        pins.pinNew(remote.deviceId(),remote.fingerprint());
        var inflight=create(file);
        assertTrue(inflight.isAlreadyPinned());
        inflight.approveKnownPinnedKey();
        inflight.acceptPeerConfirmation(proof(inflight));
        inflight.markLocalReady();
        pins.revoke(remote.deviceId());
        assertThrows(IllegalStateException.class,
            ()->inflight.acceptPeerReadyAndCommit(ready(inflight)),
            "A user revoke between CONFIRM and READY must not be silently re-pinned");
        assertFalse(inflight.canChat());
        assertFalse(pins.known(remote.deviceId()));
    }

    @Test void knownPinDoesNotRequireSecondSasButRevokedOrChangedKeyRequiresNewApproval()throws Exception{
        Path file=root.resolve("known.pin");
        PinnedPeerStore pinned=new PinnedPeerStore(file);
        pinned.pinNew(remote.deviceId(),remote.fingerprint());
        var known=create(file);
        assertTrue(known.isAlreadyPinned());
        known.approveKnownPinnedKey();
        known.acceptPeerConfirmation(proof(known));
        known.markLocalReady();
        assertNotNull(known.acceptPeerReadyAndCommit(ready(known)));
        assertTrue(known.canChat());
        pinned.revoke(remote.deviceId());
        var revoked=create(file);
        assertFalse(revoked.isAlreadyPinned());
        assertThrows(IllegalStateException.class,revoked::approveKnownPinnedKey);
        assertFalse(revoked.canChat());
        pinned.pinNew(remote.deviceId(),remote.fingerprint());
        var substituted=DeviceCertificateFactory.create(remote.deviceId());
        var changed=new PeerTrustService(local,remote.deviceId(),substituted.fingerprint(),
            ownNonce,remoteNonce,requestId,pinned,new PairingStateMachine(requestId),
            tick::get,Duration.ofSeconds(120));
        assertThrows(IllegalStateException.class,changed::isAlreadyPinned);
        assertThrows(IllegalStateException.class,()->changed.approveLocalComparison(true));
        assertFalse(changed.canChat());
    }
}
