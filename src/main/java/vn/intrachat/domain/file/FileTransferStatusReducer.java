package vn.intrachat.domain.file;

import java.util.EnumSet;
import java.util.Objects;

/** Pure sender/receiver state graph; does not authenticate an actual receiver commit. */
public final class FileTransferStatusReducer {
    public enum Stage {
        SELECTED, PREHASHING, OFFERING, AWAITING_ACCEPT, STREAMING,
        WAITING_RECEIVER_COMMIT, RECEIVING, VERIFYING, COMMITTING,
        COMPLETED, REJECTED, CANCELLED, FAILED, UNCERTAIN
    }
    private FileTransferStatusReducer() { }
    private static boolean terminal(Stage s) {
        return EnumSet.of(Stage.COMPLETED,Stage.REJECTED,Stage.CANCELLED,Stage.FAILED,Stage.UNCERTAIN).contains(s);
    }
    public static Stage advance(Stage from, Stage to) {
        Objects.requireNonNull(from);Objects.requireNonNull(to);
        if (from==to && terminal(from)) return from;
        if (terminal(from)) throw new IllegalStateException("Terminal attempt cannot resurrect");
        if (to==Stage.FAILED || to==Stage.CANCELLED || to==Stage.UNCERTAIN)
            return to;
        boolean valid = switch (from) {
          case SELECTED -> to==Stage.PREHASHING;
          case PREHASHING -> to==Stage.OFFERING;
          case OFFERING -> to==Stage.AWAITING_ACCEPT;
          case AWAITING_ACCEPT -> to==Stage.STREAMING || to==Stage.RECEIVING || to==Stage.REJECTED;
          case STREAMING -> to==Stage.WAITING_RECEIVER_COMMIT;
          case RECEIVING -> to==Stage.VERIFYING;
          case VERIFYING -> to==Stage.COMMITTING;
          case COMMITTING, WAITING_RECEIVER_COMMIT -> to==Stage.COMPLETED;
          default -> false;
        };
        if (!valid) throw new IllegalStateException("Invalid File state transition");
        return to;
    }
}
