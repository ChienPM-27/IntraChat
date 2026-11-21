package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TcpListenerTest {
    @Test void bindAcceptAndClose() throws Exception {
        int port;
        try(ServerSocket free=new ServerSocket(0)){port=free.getLocalPort();}
        CountDownLatch received=new CountDownLatch(1);
        try(TcpListener listener=new TcpListener()){
            listener.start(new NetworkConfig("127.0.0.1",port),socket->{
                try(socket){received.countDown();}
                catch(Exception ignored){}
            });
            assertTrue(listener.isListening());
            try(Socket client=new Socket("127.0.0.1",port)){
                assertTrue(received.await(3,TimeUnit.SECONDS));
            }
        }
        // Windows can keep an accepted TCP endpoint in TIME_WAIT briefly; do not require immediate rebind.
        // Listener close and process/session cleanup are covered independently by lifecycle tests.
    }
    @Test void portConflictIsVisible() throws Exception {
        try(ServerSocket busy=new ServerSocket(0)){
            try(TcpListener listener=new TcpListener()){
                assertThrows(java.io.IOException.class,()->listener.start(new NetworkConfig("127.0.0.1",busy.getLocalPort()),socket->{}));
                assertFalse(listener.isListening());
            }
        }
    }
}
