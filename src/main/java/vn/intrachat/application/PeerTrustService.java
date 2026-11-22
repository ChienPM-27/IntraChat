package vn.intrachat.application;

import vn.intrachat.domain.P2PConversation;
import vn.intrachat.domain.VerifiedPeerIdentity;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Security-critical pairing coordinator AFTER the caller has authenticated the
 * remote's TLS certificate as expected device UUID/P-256 SPKI. Human decisions
 * must still come from real GUI approval in production. Parsing protected frames
 * is not proof until request/session/sender/conversation/transcript match here.
 *
 * Neither unknown TLS peers nor a persisted pin alone authorize CHAT_TEXT.
 * A local chat-ready flag is set only after both READY proof and protected
 * pin persistence have succeeded. Failure cannot become VERIFIED.
 */
public final class PeerTrustService {
    private final DeviceIdentity own;
    private final UUID remoteId,requestId,conversationId;
    private final byte[] peerFingerprint,transcript;
    private final PinnedPeerStore pins;
    private final PairingStateMachine state;
    private final LongSupplier monotonic;
    private final long startNanos,timeoutNanos;
    private boolean localApproved,confirmed,localReady,peerReady,failed,pinnedAtApproval;
    private VerifiedPeerIdentity identity;

    public PeerTrustService(DeviceIdentity own,UUID remoteId,byte[] remoteFingerprint,
                            byte[] ownNonce,byte[] peerNonce,UUID requestId,
                            PinnedPeerStore pins,PairingStateMachine state,
                            LongSupplier clock,Duration deadline){
        this.own=Objects.requireNonNull(own);
        this.remoteId=Objects.requireNonNull(remoteId);
        this.requestId=Objects.requireNonNull(requestId);
        this.pins=Objects.requireNonNull(pins);
        this.state=Objects.requireNonNull(state);
        this.monotonic=Objects.requireNonNull(clock);
        Objects.requireNonNull(deadline);
        if(deadline.isNegative()||deadline.isZero()
            ||deadline.compareTo(Duration.ofMinutes(5))>0)
            throw new IllegalArgumentException("Invalid protected pairing timeout");
        if(remoteId.equals(own.deviceId())||remoteId.equals(new UUID(0,0))
            ||requestId.equals(new UUID(0,0))||!state.requestId().equals(requestId))
            throw new IllegalArgumentException("Invalid pair endpoint or session ID");
        if(remoteFingerprint==null||remoteFingerprint.length!=32)
            throw new IllegalArgumentException("Peer TLS fingerprint must be SHA256");
        this.peerFingerprint=remoteFingerprint.clone();
        this.transcript=FingerprintVerifier.transcriptHash(own.deviceId(),own.fingerprint(),
            ownNonce,remoteId,this.peerFingerprint,peerNonce);
        this.conversationId=P2PConversation.idFor(own.deviceId(),own.fingerprint(),
            remoteId,this.peerFingerprint);
        this.timeoutNanos=deadline.toNanos();
        this.startNanos=clock.getAsLong();
    }
    public UUID conversationId(){return conversationId;}
    public String transcriptHex(){return HexFormat.of().formatHex(transcript);}
    public boolean canChat(){
        return !failed&&identity!=null&&state.maySendChat();
    }
    public VerifiedPeerIdentity verifiedIdentity(){
        if(!canChat())throw new IllegalStateException("Bilateral pairing and pin required");
        return identity;
    }
    private void requireActive(){
        if(failed)throw new IllegalStateException("Pairing has been rejected");
        if(identity!=null)throw new IllegalStateException("Pairing already committed");
        long elapsed=monotonic.getAsLong()-startNanos;
        if(elapsed<0||elapsed>timeoutNanos){
            reject();
            throw new IllegalStateException("Protected pairing deadline exceeded");
        }
    }
    private void reject(){
        failed=true;
        state.reject();
    }
    private boolean pinnedMatch()throws IOException{
        if(!pins.known(remoteId))return false;
        if(!pins.matches(remoteId,peerFingerprint)){
            reject();
            throw new IllegalStateException("Pinned device key changed; explicit revoke and re-pair");
        }
        return true;
    }
    public boolean isAlreadyPinned()throws IOException{
        requireActive();
        return pinnedMatch();
    }
    public void approveLocalComparison(boolean identicalSas)throws IOException {
        requireActive();
        boolean wasPinned=pinnedMatch(); // never replace a changed pin
        if(!identicalSas){
            reject();
            throw new IllegalStateException("Person rejected shared verification code");
        }
        if(localApproved)throw new IllegalStateException("Duplicate local confirmation");
        pinnedAtApproval=wasPinned;
        localApproved=true;
        state.acceptLocalFingerprint();
    }
    public void approveKnownPinnedKey()throws IOException{
        requireActive();
        if(!pinnedMatch())
            throw new IllegalStateException("Unpinned peer requires explicit human SAS verification");
        pinnedAtApproval=true;
        if(localApproved)throw new IllegalStateException("Duplicate local confirmation");
        localApproved=true;
        state.acceptLocalFingerprint();
    }
    private static UUID parseNonzeroUuid(Object field){
        if(!(field instanceof String raw))throw new IllegalArgumentException("Missing UUID field");
        UUID result;
        try{result=UUID.fromString(raw);}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Malformed pairing UUID",invalid);}
        if(result.equals(new UUID(0,0))||!result.toString().equalsIgnoreCase(raw))
            throw new IllegalArgumentException("Non-canonical pairing UUID");
        return result;
    }
    private void validateProtectedControl(ChatV2Message frame,ChatV2Type type){
        // Any malformed/mismatched protected proof fails closed for this handshake.
        if(frame==null||frame.type()!=type||!requestId.equals(frame.sessionId())
            ||!remoteId.equals(frame.senderId())
            ||!own.deviceId().equals(frame.destinationId())
            ||!conversationId.equals(frame.conversationId())){
            reject();
            throw new IllegalArgumentException("Wrong authenticated pairing context");
        }
        Map<String,Object> payload=frame.payload();
        UUID correlated;
        try{
            correlated=payload==null?null:parseNonzeroUuid(payload.get("requestId"));
        }catch(IllegalArgumentException malformed){
            reject(); // malformed proof must poison the entire handshake
            throw malformed;
        }
        if(payload==null||payload.size()!=(type==ChatV2Type.PAIR_CONFIRM?3:2)
            ||!requestId.equals(correlated)){
            reject();
            throw new IllegalArgumentException("Missing or changed pairing request correlation");
        }
        Object sentHex=payload.get("transcriptHash");
        if(!(sentHex instanceof String text)||text.length()!=64
            ||!text.matches("[0-9a-f]{64}")
            ||!MessageDigest.isEqual(transcript,HexFormat.of().parseHex(text))){
            reject();
            throw new IllegalArgumentException("Pairing transcript/SAS mismatch");
        }
        if(type==ChatV2Type.PAIR_CONFIRM){
            try{parseNonzeroUuid(payload.get("confirmationId"));}
            catch(IllegalArgumentException failure){
                reject();
                throw failure;
            }
        }
    }
    public void acceptPeerConfirmation(ChatV2Message proof){
        requireActive();
        if(!localApproved)throw new IllegalStateException("Local human or known pin not confirmed");
        if(confirmed)throw new IllegalStateException("Duplicate PAIR_CONFIRM");
        validateProtectedControl(proof,ChatV2Type.PAIR_CONFIRM);
        confirmed=true;
        state.acceptRemoteConfirmation();
    }
    public void markLocalReady(){
        requireActive();
        if(!confirmed||!localApproved)throw new IllegalStateException("Both confirmations required");
        if(localReady)throw new IllegalStateException("Duplicate local READY");
        state.localReady();
        localReady=true;
    }
    public VerifiedPeerIdentity acceptPeerReadyAndCommit(ChatV2Message ready)throws IOException{
        requireActive();
        if(!localReady||!confirmed)
            throw new IllegalStateException("Local READY and protected CONFIRM required");
        if(peerReady)throw new IllegalStateException("Duplicate remote READY");
        validateProtectedControl(ready,ChatV2Type.PAIR_READY);
        // Commit trust first: a failed DPAPI/ACL write MUST NOT activate chat.
        try{
            // A concurrent explicit revoke while pairing must not be silently
            // undone by a delayed remote READY.
            if(pinnedAtApproval&&!pins.matches(remoteId,peerFingerprint))
                throw new IllegalStateException("Verified key was revoked during pairing");
            pins.pinNew(remoteId,peerFingerprint);
            if(!pins.matches(remoteId,peerFingerprint))
                throw new IOException("Protected trust commit did not persist");
        }catch(IOException|RuntimeException impossible){
            reject();
            throw impossible;
        }
        state.remoteReady();
        peerReady=true;
        identity=new VerifiedPeerIdentity(remoteId,peerFingerprint,requestId,transcript,conversationId);
        return identity;
    }
}
