package vn.intrachat.domain.file;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** Pure metadata validator, not a filesystem path resolver or Win11 no-clobber implementation. */
public final class FileDisplayBasename {
    private FileDisplayBasename() { }
    public static String requireSafe(String input) {
        Objects.requireNonNull(input, "basename");
        String name = Normalizer.normalize(input, Normalizer.Form.NFC);
        if (name.isEmpty() || name.equals(".") || name.equals("..") ||
            name.endsWith(".") || name.endsWith(" ") ||
            name.getBytes(StandardCharsets.UTF_8).length > 255)
            throw new IllegalArgumentException("Unsafe file basename");
        for (int i=0; i<name.length();) {
            int codepoint=name.codePointAt(i);
            i+=Character.charCount(codepoint);
            if (codepoint == '\\' || codepoint == '/' || codepoint == ':' ||
                codepoint == '*' || codepoint == '?' || codepoint == '"' ||
                codepoint == '<' || codepoint == '>' || codepoint == '|' ||
                codepoint < 32 || codepoint == 127 || Character.isISOControl(codepoint) ||
                Character.isSurrogate((char)codepoint) ||
                (codepoint>=0x202A && codepoint<=0x202E) ||
                (codepoint>=0x2066 && codepoint<=0x2069))
                throw new IllegalArgumentException("Unsafe file basename");
        }
        String stem = name.split("\\.", -1)[0].toUpperCase(Locale.ROOT);
        if (stem.equals("CON") || stem.equals("PRN") || stem.equals("AUX") || stem.equals("NUL") ||
            stem.matches("COM[1-9]") || stem.matches("LPT[1-9]"))
            throw new IllegalArgumentException("Windows reserved filename");
        return name;
    }
}
