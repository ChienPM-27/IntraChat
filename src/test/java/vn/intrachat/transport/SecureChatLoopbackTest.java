package vn.intrachat.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.intrachat.security.DeviceIdentityStore;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Real Windows Java21 process separation. Not two physical LAN hosts. */
class SecureChatLoopbackTest {
    @TempDir Path root;
    private static int freePort()throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            return s.getLocalPort();
        }
    }
    private Process spawn(String role,String mode,int port,UUID original,Path log)throws IOException{
        String cp=System.getProperty("surefire.test.class.path",
            System.getProperty("java.class.path"));
        String tests=Path.of("target","test-classes").toAbsolutePath().toString();
        return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-cp",tests+System.getProperty("path.separator")+cp,
            SecurePeerProcessHarness.class.getName(),role,mode,root.toString(),
            Integer.toString(port),original.toString())
            .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }
    private static String read(Path path)throws IOException{
        return Files.exists(path)?Files.readString(path):"";
    }
    private void phase(String mode,UUID original)throws Exception{
        int port=freePort();
        Path a=root.resolve(mode+"-server.log"),b=root.resolve(mode+"-client.log");
        Process server=spawn("server",mode,port,original,a);
        Process client=null;
        try{
            boolean ready=false;
            for(int attempt=0;attempt<160;attempt++){
                if(read(a).contains("TLS_LISTENING")){ready=true;break;}
                if(!server.isAlive())break;
                Thread.sleep(25);
            }
            assertTrue(ready,"Missing server ready for "+mode+": "+read(a));
            client=spawn("client",mode,port,original,b);
            assertTrue(client.waitFor(17,TimeUnit.SECONDS),"TLS client hung: "+read(b));
            assertTrue(server.waitFor(17,TimeUnit.SECONDS),"TLS server hung: "+read(a));
            assertEquals(0,client.exitValue(),"Client "+mode+": "+read(b));
            assertEquals(0,server.exitValue(),"Server "+mode+": "+read(a));
            if("changed-key".equals(mode)){
                assertTrue(read(a).contains("CHANGED_KEY_DENIED_PASS"));
                assertTrue(read(b).contains("CHANGED_KEY_DENIED_PASS"));
            }else{
                assertTrue(read(a).contains("PROTECTED_RECEIVE_PASS"),read(a));
                assertTrue(read(b).contains("PROTECTED_ACK_PASS"),read(b));
                assertTrue(read(a).contains("BIDIRECTIONAL_METADATA_PASS"),read(a));
                assertTrue(read(b).contains("BIDIRECTIONAL_METADATA_PASS"),read(b));
                int expected="first".equals(mode)?1:0;
                assertTrue(read(a).contains("COMPARISON_COUNT="+expected),read(a));
                assertTrue(read(b).contains("COMPARISON_COUNT="+expected),read(b));
            }
        }finally{
            if(client!=null&&client.isAlive()){client.destroyForcibly();client.waitFor(3,TimeUnit.SECONDS);}
            if(server.isAlive()){server.destroyForcibly();server.waitFor(3,TimeUnit.SECONDS);}
        }
    }
    @Test void twoActualJvmProcessesFirstSasThenTrustedRestartThenChangedKeyRejected()throws Exception{
        UUID original=new DeviceIdentityStore(root.resolve("client.identity"))
            .loadOrCreate().deviceId();
        phase("first",original);
        phase("restart",original);
        phase("changed-key",original);
    }
}
