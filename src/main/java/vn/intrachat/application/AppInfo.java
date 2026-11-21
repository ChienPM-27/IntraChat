package vn.intrachat.application;

/** Product identity and truthful foundation-stage capabilities. */
public final class AppInfo {
    public static final String NAME = "IntraChat";
    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final String INITIAL_CONNECTION_LABEL = "Chưa kết nối";
    public static final boolean CHAT_AVAILABLE = false;

    private AppInfo() {
        throw new AssertionError("No instances");
    }
}
