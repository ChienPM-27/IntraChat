package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Three scenarios run in TWO REAL JAVA 21 PROCESSES on ONE Windows host. */
class InboundApprovalIntegrationTest {
    @TempDir Path logs;
    private static int port()throws Exception{
        try(var socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return socket.getLocalPort();
        }
    }
    private static String javaCommand(){
        return Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
    }
    private Process launch(String side,String mode,int port,Path log)throws IOException{
        String cp=System.getProperty("surefire.test.class.path",
            System.getProperty("java.class.path"));
        // Explicit test classes protect against Surefire's temporary booter-only classpath.
        String testClasses=Path.of("target","test-classes").toAbsolutePath().toString();
        String classpath=testClasses+System.getProperty("path.separator")+cp;
        return new ProcessBuilder(javaCommand(),"-cp",classpath,
            M1PeerProcessHarness.class.getName(),side,mode,Integer.toString(port))
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static String read(Path log)throws Exception{
        return Files.exists(log)?Files.readString(log):"";
    }
    private void scenario(String mode)throws Exception{
        int port=port();
        Path serverLog=logs.resolve(mode+"-server.log");
        Path clientLog=logs.resolve(mode+"-client.log");
        Process server=launch("server",mode,port,serverLog);
        Process client=null;
        try{
            boolean ready=false;
            for(int i=0;i<150;i++){
                if(read(serverLog).contains("LISTENING")){ready=true;break;}
                if(!server.isAlive())break;
                Thread.sleep(25);
            }
            assertTrue(ready,"A failed to start: "+read(serverLog));
            client=launch("client",mode,port,clientLog);
            assertTrue(client.waitFor(9,TimeUnit.SECONDS),
                "Client process hung: "+read(clientLog));
            assertTrue(server.waitFor(9,TimeUnit.SECONDS),
                "Server process hung: "+read(serverLog));
            assertEquals(0,client.exitValue(),"Client failed: "+read(clientLog));
            assertEquals(0,server.exitValue(),"Server failed: "+read(serverLog));
            if("accept".equals(mode)){
                assertTrue(read(serverLog).contains("A_PING_PASS"),read(serverLog));
                assertTrue(read(clientLog).contains("B_PING_PASS"),read(clientLog));
            }else{
                assertTrue(read(serverLog).contains("A_DENIED_PASS"),read(serverLog));
                assertTrue(read(clientLog).contains("B_DENIED_PASS"),read(clientLog));
            }
        }finally{
            if(client!=null&&client.isAlive()){client.destroyForcibly();client.waitFor(3,TimeUnit.SECONDS);}
            if(server.isAlive()){server.destroyForcibly();server.waitFor(3,TimeUnit.SECONDS);}
        }
    }
    @Test void humanConsentScriptedAcceptInSeparateJvmProcesses()throws Exception{scenario("accept");}
    @Test void humanConsentScriptedRejectInSeparateJvmProcesses()throws Exception{scenario("reject");}
    @Test void scriptedConsentExpiryInSeparateJvmProcesses()throws Exception{scenario("timeout");}
}
