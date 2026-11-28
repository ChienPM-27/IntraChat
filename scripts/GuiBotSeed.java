import vn.intrachat.application.LocalProfile;
import vn.intrachat.persistence.HistoryRepository;
import vn.intrachat.transport.NetworkConfig;
import java.nio.file.Path;

/** Prepare isolated desktop test profiles using the same persistence as normal users. */
public class GuiBotSeed {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Usage: GuiBotSeed <session> <port>...");
        Path root = Path.of(args[0]);
        for (int i = 1; i < args.length; i++) {
            String name = String.format("Bot-%02d", i);
            Path dir = root.resolve(name);
            LocalProfile.loadOrCreate(dir, name);
            try (var history = new HistoryRepository(dir)) {
                history.saveLocalListen(new NetworkConfig("127.0.0.1", Integer.parseInt(args[i])));
                history.flush();
            }
        }
    }
}
