package ui;

import model.mock.MockChatService;
import ui.components.ChatStyle;
import ui.dialogs.LanConnectionDialog;
import ui.frames.LoginFrame;
import vn.intrachat.application.AppInfo;
import vn.intrachat.application.LocalProfile;
import vn.intrachat.application.LiveChatService;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

/** Single application entrypoint, Live TCP opt-in separate from Demo. */
public final class IntraChat {
    private static volatile boolean openingLan;
    private static LanConnectionDialog lanDialog;
    private IntraChat(){}
    public static void main(String[] args) {
        if(args.length==1 && "--diagnostics".equals(args[0])){
            System.out.println(AppInfo.NAME+" "+AppInfo.VERSION+" — TCP LAN plaintext (no encryption)");
            return;
        }
        if(args.length>0){System.err.println("Usage: java -jar intrachat.jar [--diagnostics]");return;}
        if(GraphicsEnvironment.isHeadless()){
            System.err.println("IntraChat requires a desktop.");return;
        }
        SwingUtilities.invokeLater(()->{
            ChatStyle.install();
            final LoginFrame[] launcher=new LoginFrame[1];
            launcher[0]=new LoginFrame(new MockChatService(),()->openLan(launcher[0]));
            launcher[0].setVisible(true);
        });
    }
    private static void openLan(LoginFrame launcher){
        if(lanDialog!=null&&lanDialog.isDisplayable()){
            lanDialog.setVisible(true);lanDialog.toFront();return;
        }
        if(openingLan)return;
        openingLan=true;
        Thread.ofVirtual().start(()->{
            try{
                LocalProfile profile=LocalProfile.loadOrCreate();
                LiveChatService live=new LiveChatService(profile);
                SwingUtilities.invokeLater(()->{
                    openingLan=false;
                    if(!launcher.isDisplayable()){live.close();return;}
                    lanDialog=new LanConnectionDialog(launcher,profile,live);
                    lanDialog.setVisible(true);
                });
            }catch(Exception error){
                SwingUtilities.invokeLater(()->{
                    openingLan=false;
                    JOptionPane.showMessageDialog(launcher,
                        "Không thể mở hồ sơ thiết bị: "+error.getMessage(),
                        "IntraChat — Lỗi cấu hình",JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
}
