package vn.intrachat.application;

import vn.intrachat.domain.P2PConversation;
import vn.intrachat.domain.PeerIdentity;
import vn.intrachat.protocol.*;
import vn.intrachat.security.*;
import vn.intrachat.transport.*;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Experimental M2-only direct TCP secure coordinator.
 *
 * Not wired into the user GUI and NOT a release-ready v1/v2 shared dispatcher.
 * Production must inject REAL Swing approval and fingerprint comparison ports;
 * only test code may supply scripted decisions.
 */
public final class SecureChatCoordinator implements AutoCloseable {
    private final DeviceIdentity local;
    private final String displayName;
    private final PinnedPeerStore trust;
    private final InboundApprovalPort inboundApproval;
    private final FingerprintApprovalPort fingerprintApproval;
    private final Consumer<TlsChatSession> onIncomingReady;
    private final Duration consentTimeout;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final ChatV2BootstrapCodec bootstrap=new ChatV2BootstrapCodec();
    private static final int MAX_ACTIVE=NetworkConfig.MAX_ACTIVE_SESSIONS;
    private final Map<UUID,TlsChatSession> sessions=new ConcurrentHashMap<>();
    private volatile TcpListener listener;
    private volatile boolean closed;

    public SecureChatCoordinator(DeviceIdentity local,String displayName,PinnedPeerStore trust,
            InboundApprovalPort approval,FingerprintApprovalPort fingerprintApproval,
            Consumer<TlsChatSession> onIncomingReady) {
        this(local,displayName,trust,approval,fingerprintApproval,onIncomingReady,
            Duration.ofSeconds(30));
    }
    /** Test-controlled short deadline only; production always uses 30 seconds. */
    SecureChatCoordinator(DeviceIdentity local,String displayName,PinnedPeerStore trust,
            InboundApprovalPort approval,FingerprintApprovalPort fingerprintApproval,
            Consumer<TlsChatSession> onIncomingReady,Duration consentTimeout) {
        this.consentTimeout=Objects.requireNonNull(consentTimeout);
        if(consentTimeout.isZero()||consentTimeout.isNegative()
            ||consentTimeout.compareTo(Duration.ofSeconds(30))>0)
            throw new IllegalArgumentException("Invalid inbound consent deadline");
        this.local=Objects.requireNonNull(local);
        this.displayName=Objects.requireNonNull(displayName);
        if(displayName.isBlank()||displayName.length()>128)
            throw new IllegalArgumentException("Invalid local displayName");
        this.trust=Objects.requireNonNull(trust);
        this.inboundApproval=Objects.requireNonNull(approval);
        this.fingerprintApproval=Objects.requireNonNull(fingerprintApproval);
        this.onIncomingReady=Objects.requireNonNull(onIncomingReady);
    }
    /** Secure-only listener; cannot share an endpoint simultaneously with the M1 listener. */
    public synchronized void startListening(NetworkConfig bind)throws IOException {
        if(closed)throw new IOException("Coordinator closed");
        if(listener!=null&&listener.isListening())throw new IllegalStateException("Listener already running");
        TcpListener next=new TcpListener();
        next.start(bind,this::receiveCandidate);
        listener=next;
    }
    public boolean isListening(){return listener!=null&&listener.isListening();}
    /** Count only verified, still-open TLS sessions, not stale socket entries. */
    public synchronized int activeCount(){
        return (int)sessions.values().stream().filter(TlsChatSession::canChat).count();
    }
    /** Explicit user stop; never schedules a new listener automatically. */
    public synchronized void stopListening(){if(listener!=null){listener.close();listener=null;}}
    public CompletableFuture<TlsChatSession> connect(NetworkConfig remote) {
        if(closed)return CompletableFuture.failedFuture(new IOException("Coordinator closed"));
        return CompletableFuture.supplyAsync(()->{
            try{return connectBlocking(remote);}
            catch(Exception failure){throw new CompletionException(failure);}
        },workers);
    }
    private TlsChatSession connectBlocking(NetworkConfig remote)throws Exception {
        Socket plain=TcpConnector.connect(remote);
        TlsChatSession result=null;
        boolean handedOff=false;
        try{
            UUID requestId=UUID.randomUUID();
            byte[] localNonce=nonce();
            var hello=new ChatV2BootstrapCodec.Hello(requestId,local.deviceId(),displayName,localNonce);
            FrameCodec.writeFrame(plain.getOutputStream(),bootstrap.encodeHello(hello),FrameCodec.HELLO_LIMIT);
            plain.setSoTimeout(35_000);
            var accepted=bootstrap.decodeAccept(FrameCodec.readFrame(plain.getInputStream(),FrameCodec.HELLO_LIMIT));
            if(!requestId.equals(accepted.requestId())||local.deviceId().equals(accepted.receiverId()))
                throw new ProtocolException("HANDSHAKE_INVALID","Wrong preface correlation");
            result=upgrade(plain,remote.ipv4(),remote.port(),true,accepted.receiverId(),requestId);
            plain=null; // TLS wrapper owns socket
            establish(result,accepted.receiverId(),accepted.displayName(),localNonce,
                      accepted.nonce(),remote.ipv4()+":"+remote.port());
            register(accepted.receiverId(),result);
            handedOff=true;
            return result;
        }finally{
            if(plain!=null)closeQuietly(plain);
            if(!handedOff&&result!=null)result.close();
        }
    }

    private void receiveCandidate(Socket candidate) {
        receiveCandidate(candidate,null);
    }

    /** Shared listener passes only a strictly validated CHAT_V2 HELLO. No fallback to v1. */
    public void acceptSharedCandidate(Socket candidate,byte[] validatedFirstFrame) {
        Objects.requireNonNull(validatedFirstFrame,"validatedFirstFrame");
        receiveCandidate(candidate,validatedFirstFrame);
    }

    private void receiveCandidate(Socket candidate,byte[] firstFrame) {
        Socket plain=candidate;
        TlsChatSession upgraded=null;
        boolean handedOff=false;
        UUID remoteId=null;
        try {
            if(closed)return;
            plain.setSoTimeout(5_000);
            ChatV2BootstrapCodec.Hello hello=bootstrap.decodeHello(firstFrame==null
                ? FrameCodec.readFrame(plain.getInputStream(),FrameCodec.HELLO_LIMIT)
                : firstFrame);
            if(local.deviceId().equals(hello.senderId()))
                throw new ProtocolException("IDENTITY_MISMATCH","Self connect forbidden");
            if(activeCount()>=MAX_ACTIVE&&!sessions.containsKey(hello.senderId())){
                FrameCodec.writeFrame(plain.getOutputStream(),
                    bootstrap.encodeReject(hello.requestId()),FrameCodec.HELLO_LIMIT);
                return; // No TLS handshake or user-content allocation for fifth peer.
            }
            InboundRequest request=new InboundRequest(hello.requestId(),
                plain.getRemoteSocketAddress().toString(),
                new PeerIdentity(hello.senderId(),hello.displayName()),
                Instant.now().plus(consentTimeout));
            InboundApprovalPort.Decision decision;
            try{
                decision=inboundApproval.requestApproval(request).toCompletableFuture()
                    .get(consentTimeout.toMillis(),TimeUnit.MILLISECONDS);
            }catch(InterruptedException interrupt){
                Thread.currentThread().interrupt();
                decision=InboundApprovalPort.Decision.REJECT;
            }catch(TimeoutException|ExecutionException|CancellationException rejected){
                decision=InboundApprovalPort.Decision.REJECT;
            }
            if(decision!=InboundApprovalPort.Decision.ACCEPT ||closed){
                FrameCodec.writeFrame(plain.getOutputStream(),
                    bootstrap.encodeReject(hello.requestId()),FrameCodec.HELLO_LIMIT);
                return;
            }
            byte[] localNonce=nonce();
            var response=new ChatV2BootstrapCodec.Accept(hello.requestId(),
                local.deviceId(),displayName,localNonce);
            FrameCodec.writeFrame(plain.getOutputStream(),
                bootstrap.encodeAccept(response),FrameCodec.HELLO_LIMIT);
            String host=plain.getInetAddress().getHostAddress();
            int port=plain.getPort();
            upgraded=upgrade(plain,host,port,false,hello.senderId(),hello.requestId());
            plain=null;
            establish(upgraded,hello.senderId(),hello.displayName(),localNonce,
                      hello.nonce(),request.remoteEndpoint());
            register(hello.senderId(),upgraded);
            remoteId=hello.senderId();
            onIncomingReady.accept(upgraded);
            handedOff=true;
        }catch(Exception failed){
            // Remote-supplied data and security credentials MUST NOT appear in logs.
        }finally{
            if(plain!=null)closeQuietly(plain);
            if(!handedOff&&upgraded!=null) {
                if(remoteId!=null)sessions.remove(remoteId,upgraded);
                upgraded.close();
            }
        }
    }

    private TlsChatSession upgrade(Socket raw,String host,int port,boolean client,UUID remoteId,UUID requestId)
            throws Exception {
        TlsContextFactory contextFactory=new TlsContextFactory(local,trust,remoteId,true);
        SSLSocket tls=(SSLSocket)contextFactory.context().getSocketFactory()
            .createSocket(raw,host,port,true);
        tls.setUseClientMode(client);
        tls.setEnabledProtocols(new String[]{"TLSv1.3"});
        if(!client)tls.setNeedClientAuth(true);
        tls.setSoTimeout(10_000);
        tls.startHandshake();
        X509Certificate candidate=(X509Certificate)tls.getSession().getPeerCertificates()[0];
        contextFactory.validatePeerCertificate(candidate);
        // Establish does not authorize chat here. It must complete human comparison and READY.
        return new TlsChatSession(tls,local.deviceId(),remoteId,
                                  new PairingStateMachine(requestId));
    }

    private void establish(TlsChatSession session,UUID remoteId,String remoteName,
                           byte[] localNonce,byte[] remoteNonce,String endpoint) throws Exception {
        // TLSContextFactory has already validated certificate, key possession,
        // P-256 SPKI and the remote UUID. PeerTrustService then binds BOTH
        // human/known-pin decisions and CONFIRM/READY to the same TLS identity,
        // nonces and exact request/verified conversation.
        X509Certificate remoteCertificate=(X509Certificate)session.socketPeerCertificate();
        byte[] ownFp=local.fingerprint();
        byte[] peerFp=FingerprintVerifier.spkiFingerprint(remoteCertificate.getPublicKey());
        PairingStateMachine state=session.pairingState();
        UUID request=state.requestId();
        PeerTrustService pair=new PeerTrustService(local,remoteId,peerFp,localNonce,
            remoteNonce,request,trust,state,System::nanoTime,Duration.ofMinutes(3));
        if(pair.isAlreadyPinned()){
            // A known SPKI does NOT skip the per-inbound human consent earlier.
            pair.approveKnownPinnedKey();
        }else{
            String code=FingerprintVerifier.sharedCode(local.deviceId(),ownFp,localNonce,
                remoteId,peerFp,remoteNonce);
            var comparison=new FingerprintApprovalPort.Comparison(remoteId,remoteName,
                endpoint,code,FingerprintVerifier.fullFingerprint(ownFp),
                FingerprintVerifier.fullFingerprint(peerFp));
            boolean approved;
            try{
                approved=Boolean.TRUE.equals(fingerprintApproval.requestComparison(comparison)
                    .toCompletableFuture().get(60,TimeUnit.SECONDS));
            }catch(TimeoutException|CancellationException|ExecutionException failure){
                approved=false;
            }
            pair.approveLocalComparison(approved);
        }
        UUID conversation=pair.conversationId();
        String digest=pair.transcriptHex();
        session.sendPairControl(new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_CONFIRM,
            request,local.deviceId(),remoteId,conversation,Instant.now(),
            Map.of("requestId",request.toString(),"transcriptHash",digest,
                "confirmationId",UUID.randomUUID().toString())));
        pair.acceptPeerConfirmation(session.receivePairControl());
        pair.markLocalReady();
        session.sendPairControl(new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_READY,
            request,local.deviceId(),remoteId,conversation,Instant.now(),
            Map.of("requestId",request.toString(),"transcriptHash",digest)));
        pair.acceptPeerReadyAndCommit(session.receivePairControl());
        if(!pair.canChat()||!session.canChat())
            throw new ProtocolException("PAIRING_INVALID","Protected identity not fully verified");
        session.setReadTimeout(15_000);
    }

    private synchronized void register(UUID remoteId,TlsChatSession session)throws IOException{
        if(closed)throw new IOException("Coordinator closed during handshake");
        // LiveChatService closes peer sockets when their reader exits. A former
        // CLOSED socket does not permanently consume scarce active peer capacity.
        sessions.entrySet().removeIf(entry->!entry.getValue().canChat());
        if(sessions.size()>=MAX_ACTIVE&&!sessions.containsKey(remoteId))
            throw new IOException("M2_ACTIVE_PEER_CAP");
        TlsChatSession old=sessions.putIfAbsent(remoteId,session);
        if(old!=null&&old.canChat())throw new IOException("Duplicate verified peer session");
        if(old!=null){sessions.put(remoteId,session);old.close();}
    }
    public void disconnect(UUID remoteId) {
        TlsChatSession session=sessions.remove(remoteId);
        if(session!=null)session.close();
    }
    private static byte[] nonce(){byte[] nonce=new byte[32];new SecureRandom().nextBytes(nonce);return nonce;}
    private static void closeQuietly(Socket s){try{s.close();}catch(IOException ignored){}}
    @Override public synchronized void close(){
        closed=true;
        if(listener!=null){listener.close();listener=null;}
        for(TlsChatSession session:sessions.values())session.close();
        sessions.clear();
        workers.shutdownNow();
    }
}
