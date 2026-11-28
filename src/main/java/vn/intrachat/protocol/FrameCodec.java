package vn.intrachat.protocol;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/** Strict 4-byte big-endian length prefixed frames on a TCP byte stream. */
public final class FrameCodec {
    public static final int CONTROL_LIMIT = 1_048_576;
    public static final int HELLO_LIMIT = 16_384;

    private FrameCodec() {}

    public static byte[] readFrame(InputStream input, int maxBytes) throws IOException {
        Objects.requireNonNull(input, "input");
        validateCap(maxBytes);
        // Đọc 4 byte đầu để biết độ dài phần thân frame.
        byte[] prefix = input.readNBytes(4);
        if (prefix.length < 4) throw new EOFException("FRAME_TRUNCATED: incomplete 4-byte length");
        long length = ((long) (prefix[0] & 0xff) << 24)
                | ((long) (prefix[1] & 0xff) << 16)
                | ((long) (prefix[2] & 0xff) << 8)
                | (long) (prefix[3] & 0xff);
        if (length == 0 || length > maxBytes) {
            throw new ProtocolException("FRAME_INVALID_LENGTH", "Frame length is outside allowed bound");
        }
        // Đọc đúng số byte đã khai báo.
        byte[] data = input.readNBytes((int) length);
        if (data.length != (int) length) {
            throw new EOFException("FRAME_TRUNCATED: peer closed before full body");
        }
        return data;
    }

    public static void writeFrame(OutputStream output, byte[] data, int maxBytes) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(data, "data");
        validateCap(maxBytes);
        if (data.length == 0 || data.length > maxBytes) {
            throw new ProtocolException("FRAME_INVALID_LENGTH", "Frame length is outside allowed bound");
        }
        // Ghi độ dài 4 byte big-endian trước nội dung frame.
        int n = data.length;
        output.write((n >>> 24) & 0xff);
        output.write((n >>> 16) & 0xff);
        output.write((n >>> 8) & 0xff);
        output.write(n & 0xff);
        output.write(data);
        output.flush();
    }

    private static void validateCap(int cap) {
        if (cap < 1 || cap > CONTROL_LIMIT) throw new IllegalArgumentException("Invalid frame cap");
    }
}
