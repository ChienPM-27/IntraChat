import java.net.InetAddress;
import java.net.Socket;
import java.security.Permission;

/**
 * Java 21-only manual verification harness, intentionally not part of the Maven app.
 * Denies Java networking for this JVM while allowing Swing to run.
 * Java SecurityManager is deprecated, so this is not a production protection mechanism.
 */
@SuppressWarnings("removal")
public final class OfflineLaunchHarness {
    private OfflineLaunchHarness() { }

    public static void main(String[] args) {
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) { }
            @Override public void checkConnect(String host, int port) {
                throw new SecurityException("Network denied by offline M0 harness");
            }
            @Override public void checkListen(int port) {
                throw new SecurityException("Network denied by offline M0 harness");
            }
            @Override public void checkAccept(String host, int port) {
                throw new SecurityException("Network denied by offline M0 harness");
            }
            @Override public void checkMulticast(InetAddress address) {
                throw new SecurityException("Network denied by offline M0 harness");
            }
        });
        try (Socket ignored = new Socket("127.0.0.1", 9)) {
            throw new AssertionError("Network isolation did not take effect");
        } catch (SecurityException expected) {
            System.out.println("OFFLINE_DENIAL_CONFIRMED");
        } catch (Exception unexpected) {
            throw new AssertionError("Unexpected exception proving network denial", unexpected);
        }
        vn.intrachat.IntraChatApplication.main(args);
    }
}
