package model;

import java.util.Objects;

public record MessageContent(MessageType type, String text, Attachment attachment) {
    public static final int MAX_TEXT_LENGTH = 8_000;

    public MessageContent {
        Objects.requireNonNull(type);
        if (type == MessageType.TEXT) {
            if (attachment != null) throw new IllegalArgumentException("Chỉ gửi văn bản trong tin text.");
            // Same code-point AND UTF-8 byte limits as real protected CHAT_V2; no silent emoji loss.
            new vn.intrachat.domain.ChatText(text);
        } else if (attachment == null || text != null) {
            throw new IllegalArgumentException("Tin đa phương tiện cần một tệp đính kèm.");
        }
    }

    public static MessageContent text(String text) {
        return new MessageContent(MessageType.TEXT, text, null);
    }

    public static MessageContent media(MessageType type, Attachment attachment) {
        return new MessageContent(type, null, attachment);
    }

    public String preview() {
        return switch (type) {
            case TEXT -> text.replaceAll("\\R", " ");
            case IMAGE -> "Ảnh";
            case VIDEO -> "Video";
            case FILE -> "Tệp · " + attachment.fileName();
            case SOUND -> "Tin nhắn thoại";
        };
    }
}
