package ui.components;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import java.awt.Window;

/** Fail-closed OS foreground check for READ_RECEIPT, beyond Swing's stale focus state. */
public final class WindowsForegroundGuard {
    private WindowsForegroundGuard(){}
    public static boolean isForeground(Window window){
        if(window==null||!window.isShowing())return false;
        try {
            long own=Native.getComponentID(window);
            var front=User32.INSTANCE.GetForegroundWindow();
            return front!=null&&sameHandle(own,Pointer.nativeValue(front.getPointer()));
        }catch(RuntimeException|LinkageError failure){return false;}
    }
    public static boolean sameHandle(long own,long active){
        return own!=0&&active!=0&&own==active;
    }
}
