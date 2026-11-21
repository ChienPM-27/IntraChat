package vn.intrachat.domain.history;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Validated, immutable text/emoji content. No drafts, files or serialized Java objects. */
public record HistoryContent(Kind kind, String value) {
    public enum Kind { TEXT, EMOJI }
    public static final int MAX_CODE_POINTS = 8000;
    public static final int MAX_UTF8_BYTES = 32 * 1024;

    public HistoryContent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        int codePoints = value.codePointCount(0,value.length());
        if (value.isBlank() || codePoints > MAX_CODE_POINTS ||
            value.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES)
            throw new IllegalArgumentException("Invalid History text size");
        for (int i=0; i<value.length(); i++) {
            char c=value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i+1>=value.length() || !Character.isLowSurrogate(value.charAt(++i)))
                    throw new IllegalArgumentException("Unpaired high surrogate");
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired low surrogate");
            } else if (Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t') {
                throw new IllegalArgumentException("Unsupported control character");
            }
        }
    }
    public static HistoryContent text(String value) { return new HistoryContent(Kind.TEXT,value); }
    public static HistoryContent emoji(String value) { return new HistoryContent(Kind.EMOJI,value); }
}
