package vn.intrachat.transport;

import vn.intrachat.protocol.FrameCodec;
import vn.intrachat.protocol.ProtocolException;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Explicit user-started shared TCP listener for M1 diagnostic and M2 secure
 * bootstrap. A single bounded first frame selects the profile; never retries
 * or downgrades after a failed decode. Each handler owns closing its socket.
 *
 * M3_GROUP_UNSUPPORTED is intentionally rejected until the group TLS profile,
 * identity binding and membership security gate are independently verified.
 */
public final class SharedProtocolListener implements AutoCloseable {
    private volatile TcpListener listener;
    private final ProtocolDispatch dispatcher=new ProtocolDispatch();
    private final BiConsumer<Socket,byte[]> m1;
    private final BiConsumer<Socket,byte[]> m2;

    public SharedProtocolListener(BiConsumer<Socket,byte[]> diagnosticHandler,
                                  BiConsumer<Socket,byte[]> secureHandler) {
        m1=Objects.requireNonNull(diagnosticHandler,"diagnosticHandler");
        m2=Objects.requireNonNull(secureHandler,"secureHandler");
    }

    public synchronized void start(NetworkConfig bind)throws IOException {
        Objects.requireNonNull(bind,"bind");
        if(listener!=null&&listener.isListening())
            throw new IllegalStateException("Shared listener already running");
        if(listener!=null)listener.close();
        TcpListener next=new TcpListener();
        try{
            next.start(bind,this::dispatch);
            listener=next;
        }catch(IOException|RuntimeException error){
            next.close();
            throw error;
        }
    }
    public boolean isListening(){return listener!=null&&listener.isListening();}

    private void dispatch(Socket candidate){
        boolean handedOff=false;
        try{
            candidate.setSoTimeout(NetworkConfig.HELLO_TIMEOUT_MS);
            byte[] first=FrameCodec.readFrame(candidate.getInputStream(),FrameCodec.HELLO_LIMIT);
            ProtocolDispatch.Profile profile=dispatcher.inspect(first);
            switch(profile){
                case M1_DIAGNOSTICS -> {m1.accept(candidate,first);handedOff=true;}
                case M2_TLS13 -> {m2.accept(candidate,first);handedOff=true;}
                case M3_GROUP_UNSUPPORTED -> {
                    // Do not pass plaintext v3 to M1 or v2 secure handler.
                    throw new ProtocolException("VERSION_UNSUPPORTED","Group V3 transport not enabled");
                }
            }
        }catch(IOException|RuntimeException malformed){
            // Never log untrusted bytes, private payload or identifying host data.
        }finally {
            if(!handedOff)try{candidate.close();}catch(IOException ignored){}
        }
    }

    @Override public synchronized void close(){
        if(listener!=null){listener.close();listener=null;}
    }
}
