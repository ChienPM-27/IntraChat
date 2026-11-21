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
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final ChatV2BootstrapCodec bootstrap=new ChatV2BootstrapCodec();
    private final Map<UUID,TlsChatSession> sessions=new ConcurrentHashMap<>();
    private volatile TcpListener listener;
    private volatile boolean closed;

    public SecureChatCoordinator(DeviceIdentity local,String displayName,PinnedPeerStore trust,
            InboundApprovalPort approval,FingerprintApprovalPort fingerprintApproval,
            Consumer<TlsChatSession> onIncomingReady) {
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
        Socket plain=candidate;
        TlsChatSession upgraded=null;
        boolean handedOff=false;
        UUID remoteId=null;
        try {
            if(closed)return;
            plain.setSoTimeout(5_000);
            ChatV2BootstrapCodec.Hello hello=bootstrap.decodeHello(
                FrameCodec.readFrame(plain.getInputStream(),FrameCodec.HELLO_LIMIT));
            if(local.deviceId().equals(hello.senderId()))
                throw new ProtocolException("IDENTITY_MISMATCH","Self connect forbidden");
            InboundRequest request=new InboundRequest(hello.requestId(),
                plain.getRemoteSocketAddress().toString(),
                new PeerIdentity(hello.senderId(),hello.displayName()),
                Instant.now().plusSeconds(30));
            InboundApprovalPort.Decision decision;
            try{
                decision=inboundApproval.requestApproval(request).toCompletableFuture()
                    .get(30,TimeUnit.SECONDS);
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
        X509Certificate remoteCertificate=(X509Certificate)session.socketPeerCertificate();
        byte[] ownFp=local.fingerprint();
        byte[] peerFp=FingerprintVerifier.spkiFingerprint(remoteCertificate.getPublicKey());
        byte[] transcript=FingerprintVerifier.transcriptHash(
            local.deviceId(),ownFp,localNonce,remoteId,peerFp,remoteNonce);
        String transcriptHex=HexFormat.of().formatHex(transcript);
        if(!trust.known(remoteId)){
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
            if(!approved)throw new ProtocolException("PAIRING_REJECTED","Human fingerprint comparison required");
        } else if(!trust.matches(remoteId,peerFp)) {
            throw new ProtocolException("PEER_KEY_CHANGED","Pinned device key differs");
        }
        PairingStateMachine state=session.pairingState();
        state.acceptLocalFingerprint();
        UUID request=state.requestId();
        UUID conversation=P2PConversation.idFor(local.deviceId(),ownFp,remoteId,peerFp);
        Map<String,Object> proof=Map.of("requestId",request.toString(),
            "transcriptHash",transcriptHex,"confirmationId",UUID.randomUUID().toString());
        session.sendPairControl(new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_CONFIRM,
            request,local.deviceId(),remoteId,conversation,Instant.now(),proof));
        ChatV2Message peerProof=session.receivePairControl();
        if(peerProof.type()!=ChatV2Type.PAIR_CONFIRM
            ||!request.toString().equals(peerProof.payload().get("requestId"))
            ||!transcriptHex.equals(peerProof.payload().get("transcriptHash"))
            ||!conversation.equals(peerProof.conversationId()))
            throw new ProtocolException("PAIRING_INVALID","Remote fingerprint confirmation mismatch");
        state.acceptRemoteConfirmation();
        // A pin persisted after verified confirmations, before READY. A partial crash cannot
        // authorize any application message until a new secure session completes READY.
        trust.pinNew(remoteId,peerFp);
        state.localReady();
        session.sendPairControl(new ChatV2Message(UUID.randomUUID(),ChatV2Type.PAIR_READY,
            request,local.deviceId(),remoteId,conversation,Instant.now(),
            Map.of("requestId",request.toString(),"transcriptHash",transcriptHex)));
        ChatV2Message peerReady=session.receivePairControl();
        if(peerReady.type()!=ChatV2Type.PAIR_READY
            ||!request.toString().equals(peerReady.payload().get("requestId"))
            ||!transcriptHex.equals(peerReady.payload().get("transcriptHash"))
            ||!conversation.equals(peerReady.conversationId()))
            throw new ProtocolException("PAIRING_INVALID","Peer READY missing/mismatched");
        state.remoteReady();
        session.setReadTimeout(15_000);
    }

    private void register(UUID remoteId,TlsChatSession session)throws IOException{
        if(closed)throw new IOException("Coordinator closed during handshake");
        TlsChatSession old=sessions.putIfAbsent(remoteId,session);
        if(old!=null&&old.canChat())throw new IOException("Duplicate verified peer session");
        if(old!=null)sessions.put(remoteId,session);
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
