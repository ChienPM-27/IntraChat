package vn.intrachat.protocol;

import java.io.IOException;

/** Bounded, user-displayable protocol failure (no raw peer payload in messages). */
public final class ProtocolException extends IOException {
    private final String code;

    public ProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }
    public ProtocolException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    public String code() { return code; }
}
