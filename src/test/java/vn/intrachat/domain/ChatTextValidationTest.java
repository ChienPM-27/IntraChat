package vn.intrachat.domain;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ChatTextValidationTest {
    @Test void vietnameseCombiningEmojiAndMultilineAreLossless() {
        String text="Xin chào 👩🏽‍💻\nHòa Bình";
        ChatText value=new ChatText(text);
        assertEquals(text,value.text());
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8),value.bytes());
    }
    @Test void rejectsBlankBrokenUnicodeAndExcessiveBytes() {
        assertThrows(IllegalArgumentException.class,()->new ChatText("  \n\t "));
        assertThrows(IllegalArgumentException.class,()->new ChatText("\uD800"));
        assertThrows(IllegalArgumentException.class,()->new ChatText("a".repeat(8001)));
        assertThrows(IllegalArgumentException.class,()->new ChatText("😀".repeat(8001)));
        assertThrows(IllegalArgumentException.class,()->new ChatText("🚀".repeat(8000)+"x"));
        assertDoesNotThrow(()->new ChatText("a".repeat(8000)));
    }
}
