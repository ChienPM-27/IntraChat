package vn.intrachat.protocol;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FrameCodecTest {
    @Test void fragmentAndTwoFramesAreDecoded() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        FrameCodec.writeFrame(raw, "Xin chào 👋".getBytes(StandardCharsets.UTF_8), 1024);
        FrameCodec.writeFrame(raw, "second".getBytes(StandardCharsets.UTF_8), 1024);
        ByteArrayInputStream input = new ByteArrayInputStream(raw.toByteArray()) {
            @Override public synchronized int read(byte[] data,int off,int len) {
                return super.read(data,off,Math.min(len,1));
            }
        };
        assertEquals("Xin chào 👋", new String(FrameCodec.readFrame(input, 1024),StandardCharsets.UTF_8));
        assertEquals("second", new String(FrameCodec.readFrame(input, 1024),StandardCharsets.UTF_8));
    }
    @Test void lengthBoundariesAndTruncationAreRejected() {
        assertThrows(ProtocolException.class,()->FrameCodec.readFrame(new ByteArrayInputStream(new byte[]{0,0,0,0}),1024));
        assertThrows(ProtocolException.class,()->FrameCodec.readFrame(new ByteArrayInputStream(new byte[]{0,0,4,1}),1024));
        assertThrows(ProtocolException.class,()->FrameCodec.readFrame(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1}),1024));
        assertThrows(EOFException.class,()->FrameCodec.readFrame(new ByteArrayInputStream(new byte[]{0,0}),1024));
        assertThrows(EOFException.class,()->FrameCodec.readFrame(new ByteArrayInputStream(new byte[]{0,0,0,3,1,2}),1024));
    }
    @Test void strictHandshakeAndControlByteCaps() throws Exception {
        byte[] hello = new byte[FrameCodec.HELLO_LIMIT];
        byte[] tooLargeHello = new byte[FrameCodec.HELLO_LIMIT + 1];
        byte[] fullControl = new byte[FrameCodec.CONTROL_LIMIT];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        FrameCodec.writeFrame(buffer, hello, FrameCodec.HELLO_LIMIT);
        assertArrayEquals(hello, FrameCodec.readFrame(
                new ByteArrayInputStream(buffer.toByteArray()), FrameCodec.HELLO_LIMIT));
        assertThrows(ProtocolException.class, () ->
                FrameCodec.writeFrame(new ByteArrayOutputStream(), tooLargeHello, FrameCodec.HELLO_LIMIT));
        buffer.reset();
        FrameCodec.writeFrame(buffer, tooLargeHello, FrameCodec.CONTROL_LIMIT);
        assertThrows(ProtocolException.class, () ->
                FrameCodec.readFrame(new ByteArrayInputStream(buffer.toByteArray()), FrameCodec.HELLO_LIMIT));
        buffer.reset();
        FrameCodec.writeFrame(buffer, fullControl, FrameCodec.CONTROL_LIMIT);
        assertEquals(FrameCodec.CONTROL_LIMIT, FrameCodec.readFrame(
                new ByteArrayInputStream(buffer.toByteArray()), FrameCodec.CONTROL_LIMIT).length);
        byte[] oversizedPrefix = {0x00, 0x10, 0x00, 0x01};
        assertThrows(ProtocolException.class, () ->
                FrameCodec.readFrame(new ByteArrayInputStream(oversizedPrefix), FrameCodec.CONTROL_LIMIT));
    }

    @Test void writeBoundariesAreEnforced() throws Exception {
        assertThrows(ProtocolException.class,()->FrameCodec.writeFrame(new ByteArrayOutputStream(),new byte[1025],1024));
        assertThrows(ProtocolException.class,()->FrameCodec.writeFrame(new ByteArrayOutputStream(),new byte[0],1024));
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        FrameCodec.writeFrame(out,new byte[1024],1024);
        assertEquals(1028,out.size());
    }
}
