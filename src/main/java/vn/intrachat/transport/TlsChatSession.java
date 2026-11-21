package vn.intrachat.transport;

import vn.intrachat.application.PairingStateMachine;
import vn.intrachat.protocol.*;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prototype protected I/O for M2. Does not authorize chat until explicit two-sided pair READY.
 * Production must also bind user decisions to the authenticated TLS transcript.
 */
public final class TlsChatSession implements AutoCloseable {
    private final SSLSocket socket;
    private final UUID local,remote;
    private final PairingStateMachine pairing;
    private final ChatV2Codec codec=new ChatV2Codec();
    private final Object sendLock=new Object();
    private final AtomicBoolean closed=new AtomicBoolean();
    public TlsChatSession(SSLSocket socket,UUID local,UUID remote,PairingStateMachine pairing){
        this.socket=Objects.requireNonNull(socket);
        this.local=Objects.requireNonNull(local);
        this.remote=Objects.requireNonNull(remote);
        this.pairing=Objects.requireNonNull(pairing);
    }
    public boolean canChat(){
        return !closed.get()&&socket.isConnected()&&!socket.isClosed()
            &&"TLSv1.3".equals(socket.getSession().getProtocol())
            &&pairing.maySendChat();
    }
    public UUID sessionId(){return pairing.requestId();}
    public UUID localId(){return local;}
    public UUID remoteId(){return remote;}
    public byte[] remoteFingerprint() throws javax.net.ssl.SSLPeerUnverifiedException {
        return vn.intrachat.security.FingerprintVerifier.spkiFingerprint(
            socket.getSession().getPeerCertificates()[0].getPublicKey());
    }
    public PairingStateMachine pairingState(){return pairing;}
    public java.security.cert.Certificate socketPeerCertificate() throws javax.net.ssl.SSLPeerUnverifiedException {
        return socket.getSession().getPeerCertificates()[0];
    }
    public void setReadTimeout(int millis)throws java.net.SocketException {socket.setSoTimeout(millis);}
    private void requireTls(){
        if(closed.get()||socket.isClosed()||!socket.isConnected()
            ||!"TLSv1.3".equals(socket.getSession().getProtocol()))
            throw new IllegalStateException("No established TLS1.3 session");
    }
    /** Pair control is permitted inside TLS quarantine; never arbitrary user content. */
    public void sendPairControl(ChatV2Message msg)throws IOException{
        requireTls();
        if(msg.type()!=ChatV2Type.PAIR_CONFIRM&&msg.type()!=ChatV2Type.PAIR_READY)
            throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE","No chat payload during pairing");
        if(!local.equals(msg.senderId())||!remote.equals(msg.destinationId())
            ||!pairing.requestId().equals(msg.sessionId()))
            throw new ProtocolException("IDENTITY_MISMATCH","Invalid pair control identity/session");
        synchronized(sendLock){FrameCodec.writeFrame(socket.getOutputStream(),codec.encode(msg),ChatV2Codec.LIMIT);}
    }
    public ChatV2Message receivePairControl()throws IOException{
        requireTls();
        ChatV2Message msg=codec.decode(FrameCodec.readFrame(socket.getInputStream(),ChatV2Codec.LIMIT));
        if(msg.type()!=ChatV2Type.PAIR_CONFIRM&&msg.type()!=ChatV2Type.PAIR_READY
            ||!remote.equals(msg.senderId())||!local.equals(msg.destinationId())
            ||!pairing.requestId().equals(msg.sessionId()))
            throw new ProtocolException("HANDSHAKE_INVALID","Invalid protected pair control");
        return msg;
    }
    public void send(ChatV2Message msg)throws IOException{
        requireAuthorization();
        if(!local.equals(msg.senderId())||!remote.equals(msg.destinationId())
            ||!pairing.requestId().equals(msg.sessionId()))
            throw new ProtocolException("IDENTITY_MISMATCH","Wrong sender/destination/session for this TLS link");
        byte[] frame=codec.encode(msg);
        synchronized(sendLock){
            requireAuthorization();
            FrameCodec.writeFrame(socket.getOutputStream(),frame,ChatV2Codec.LIMIT);
        }
    }
    public ChatV2Message receive()throws IOException{
        requireAuthorization();
        ChatV2Message msg=codec.decode(FrameCodec.readFrame(socket.getInputStream(),ChatV2Codec.LIMIT));
        if(!remote.equals(msg.senderId())||!local.equals(msg.destinationId())
            ||!pairing.requestId().equals(msg.sessionId()))
            throw new ProtocolException("IDENTITY_MISMATCH","Wrong authenticated peer/message/session");
        return msg;
    }
    private void requireAuthorization(){
        if(!canChat())throw new IllegalStateException("M2 chat forbidden until mutual TLS and two-sided pairing READY");
    }
    @Override public void close(){
        if(closed.compareAndSet(false,true)){
            try{socket.close();}catch(IOException ignored){}
        }
    }
}
