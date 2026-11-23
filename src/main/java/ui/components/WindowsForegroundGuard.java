package ui.components;
import java.awt.*;
/** Foreground guard using Java AWT focus instead of native dependencies. */
public final class WindowsForegroundGuard {
    private WindowsForegroundGuard(){}
    public static boolean isForeground(Window window){
        return window!=null && window.isShowing() && window.isActive() && window.isFocused()
            && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()==window;
    }
}
