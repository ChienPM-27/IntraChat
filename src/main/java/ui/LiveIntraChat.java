package ui;

import com.formdev.flatlaf.FlatLightLaf;
import ui.components.ChatStyle;
import ui.frames.LiveConnectionFrame;
import vn.intrachat.security.*;

import javax.swing.*;
import java.nio.file.*;

/** Separate FE Messenger Live entrypoint; existing ui.IntraChat always remains the mock Demo. */
public final class LiveIntraChat {
    private LiveIntraChat(){ }
    public static void main(String[] args) {
        try {
            String local=System.getenv("LOCALAPPDATA");
            if(local==null||local.isBlank())
                throw new IllegalStateException("Windows LOCALAPPDATA unavailable");
            Path base=Path.of(System.getProperty("intrachat.secureRoot",
                Path.of(local,"IntraChat","security").toString())).toAbsolutePath().normalize();
            DeviceIdentity identity=new DeviceIdentityStore(base.resolve("identity.bin")).loadOrCreate();
            PinnedPeerStore peers=new PinnedPeerStore(base.resolve("peers.bin"));
            SwingUtilities.invokeLater(()->{
                ChatStyle.install();
                new LiveConnectionFrame(identity,peers).setVisible(true);
            });
        }catch(Exception failure) {
            System.err.println("IntraChat Live security store unavailable: "+failure.getClass().getSimpleName());
            SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,
                "Không thể mở danh tính bảo vệ Windows DPAPI. Không kết nối Chat Live.\n"+
                "Hãy kiểm tra quyền truy cập hoặc khôi phục danh tính một cách có chủ đích.",
                "IntraChat Live — Lỗi bảo mật",JOptionPane.ERROR_MESSAGE));
        }
    }
}
