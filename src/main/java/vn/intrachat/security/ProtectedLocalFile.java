package vn.intrachat.security;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Windows CurrentUser DPAPI. All writes are atomic and restricted to the file owner. */
final class ProtectedLocalFile {
    private ProtectedLocalFile(){}
    static byte[] read(Path file)throws IOException{
        if(!Files.exists(file))throw new IOException("Protected store missing");
        try {
            byte[] encrypted=Files.readAllBytes(file);
            if(encrypted.length==0||encrypted.length>1_048_576)throw new IOException("Invalid protected store size");
            return Crypt32Util.cryptUnprotectData(encrypted);
        }catch(RuntimeException nativeFailure){throw new IOException("Windows user-scope credential decryption failed",nativeFailure);}
    }
    static void write(Path file,byte[] clear)throws IOException {
        Objects.requireNonNull(file);Objects.requireNonNull(clear);
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows"))
            throw new IOException("DPAPI store only supports Windows");
        Path parent=file.toAbsolutePath().normalize().getParent();
        if(parent==null)throw new IOException("Missing secure store parent");
        Files.createDirectories(parent);
        byte[] ciphertext;
        try { ciphertext=Crypt32Util.cryptProtectData(clear); }
        catch(RuntimeException e){throw new IOException("DPAPI protection failed",e);}
        Path temporary=null;
        try {
            temporary=Files.createTempFile(parent,".intrachat-",".tmp");
            restrictAcl(temporary);
            Files.write(temporary,ciphertext,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            try {Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){throw new IOException("Atomic secure-store replace unavailable",unsupported);}
            temporary=null;
        }finally{
            if(temporary!=null)Files.deleteIfExists(temporary);
            Arrays.fill(ciphertext,(byte)0);
        }
    }
    private static void restrictAcl(Path file)throws IOException{
        AclFileAttributeView view=Files.getFileAttributeView(file,AclFileAttributeView.class);
        if(view==null)throw new IOException("Windows ACL unavailable");
        UserPrincipal owner=Files.getOwner(file);
        AclEntry entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
        view.setAcl(List.of(entry));
    }
}
