package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpConnectorTest {
    @Test void connectsToUserSpecifiedIpAndPort() throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
            Socket socket=TcpConnector.connect(new NetworkConfig("127.0.0.1",server.getLocalPort()))){
            assertTrue(socket.isConnected());
            try(Socket accepted=server.accept()){assertNotNull(accepted);}
        }
    }
    @Test void refusedPortProducesIOException() throws Exception {
        int port;
        try(ServerSocket free=new ServerSocket(0)){port=free.getLocalPort();}
        assertThrows(IOException.class,()->TcpConnector.connect(new NetworkConfig("127.0.0.1",port)));
    }
}
