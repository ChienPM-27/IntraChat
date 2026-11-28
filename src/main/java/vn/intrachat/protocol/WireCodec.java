package vn.intrachat.protocol;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
/** Bounded UTF-8 JSON frames; unknown fields and malformed text rejected. */
public final class WireCodec {
    public static final int MAX_FRAME=1_048_576;
    private static final ObjectMapper JSON=new ObjectMapper();
    // Chuyển WireMessage thành JSON UTF-8.
    public byte[] encode(WireMessage msg)throws IOException{
        var n=JSON.createObjectNode();
        n.put("type",msg.type().name());
        if(msg.messageId()!=null)n.put("messageId",msg.messageId().toString());
        if(msg.conversationId()!=null)n.put("conversationId",msg.conversationId().toString());
        n.put("senderId",msg.senderId().toString());
        if(msg.recipientId()!=null)n.put("recipientId",msg.recipientId().toString());
        if(msg.sentAtUtc()!=null)n.put("sentAtUtc",msg.sentAtUtc().toString());
        n.set("payload",JSON.valueToTree(msg.payload()));
        byte[] bytes=JSON.writeValueAsBytes(n);
        if(bytes.length>MAX_FRAME)throw new IOException("Frame size exceeded");
        return bytes;
    }
    private static UUID uuid(JsonNode node,String key){
        JsonNode v=node.get(key);
        return v==null||v.isNull()?null:UUID.fromString(v.asText());
    }
    // Kiểm tra và giải mã JSON thành WireMessage.
    public WireMessage decode(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length==0||bytes.length>MAX_FRAME)
            throw new IOException("Invalid wire length");
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
            JsonNode n=JSON.readTree(bytes);
            if(n==null||!n.isObject()||!n.path("type").isTextual()
                ||!n.path("senderId").isTextual()||!n.path("payload").isObject())
                throw new IOException("Invalid envelope");
            var fields=n.fieldNames();
            while(fields.hasNext())if(!Set.of("type","messageId","conversationId","senderId",
                "recipientId","sentAtUtc","payload").contains(fields.next()))
                throw new IOException("Unknown field");
            Map<String,String> payload=new HashMap<>();
            n.path("payload").fields().forEachRemaining(e->{
                if(!e.getValue().isTextual())throw new IllegalArgumentException("Invalid payload entry");
                payload.put(e.getKey(),e.getValue().textValue());
            });
            JsonNode time=n.get("sentAtUtc");
            return new WireMessage(WireMessage.Type.valueOf(n.get("type").textValue()),
                uuid(n,"messageId"),uuid(n,"conversationId"),uuid(n,"senderId"),
                uuid(n,"recipientId"),time==null?null:Instant.parse(time.asText()),payload);
        }catch(RuntimeException ex){throw new IOException("Malformed wire frame",ex);}
    }
    public void write(OutputStream out,WireMessage msg)throws IOException{
        FrameCodec.writeFrame(out,encode(msg),MAX_FRAME);out.flush();
    }
    public WireMessage read(InputStream in)throws IOException{
        return decode(FrameCodec.readFrame(in,MAX_FRAME));
    }
}
