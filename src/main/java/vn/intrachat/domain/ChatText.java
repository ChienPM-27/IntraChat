package vn.intrachat.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable M2 text without silent UTF-8 loss or truncation. */
public record ChatText(String text) {
    public static final int MAX_CODE_POINTS=8_000;
    public static final int MAX_UTF8_BYTES=32*1024;
    public ChatText {
        Objects.requireNonNull(text,"text");
        if(text.isBlank())throw new IllegalArgumentException("Message is blank");
        int cps=0;
        for(int i=0;i<text.length();) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1>=text.length()||!Character.isLowSurrogate(text.charAt(i+1)))
                    throw new IllegalArgumentException("Invalid Unicode surrogate");
                i+=2;
            } else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode surrogate");
            else i++;
            if(++cps>MAX_CODE_POINTS)throw new IllegalArgumentException("Too many Unicode code points");
        }
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_UTF8_BYTES)
            throw new IllegalArgumentException("Message exceeds 32KiB UTF-8");
    }
    public byte[] bytes(){return text.getBytes(StandardCharsets.UTF_8);}
}
