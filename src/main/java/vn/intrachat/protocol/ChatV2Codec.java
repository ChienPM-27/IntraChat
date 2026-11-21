package vn.intrachat.protocol;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import vn.intrachat.domain.ChatText;

/** Only accepted on a peer-authenticated TLS1.3 stream AFTER both peers are READY. */
public final class ChatV2Codec {
    public static final int LIMIT=65_536;
    private static final Set<String> ROOT=Set.of(
        "protocolVersion","messageId","type","sessionId","sender","destination",
        "conversationId","sentAt","payload");
    private static final Set<String> SENDER=Set.of("deviceId");
    private static final Set<String> DEST=Set.of("kind","id");
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(32).maxStringLength(65_536).maxNumberLength(64).build()).build());

    public byte[] encode(ChatV2Message input)throws ProtocolException {
        validate(input);
        ObjectNode root=mapper.createObjectNode();
        root.put("protocolVersion",2);
        root.put("messageId",input.messageId().toString());
        root.put("type",input.type().name());
        root.put("sessionId",input.sessionId().toString());
        root.putObject("sender").put("deviceId",input.senderId().toString());
        ObjectNode dest=root.putObject("destination");
        dest.put("kind","PEER");dest.put("id",input.destinationId().toString());
        root.put("conversationId",input.conversationId().toString());
        root.put("sentAt",input.sentAt().toString());
        root.set("payload",mapper.valueToTree(input.payload()));
        try {
            byte[] raw=mapper.writeValueAsBytes(root);
            if(raw.length==0||raw.length>LIMIT)throw new ProtocolException("FRAME_INVALID_LENGTH","v2 frame too large");
            return raw;
        }catch(IOException e){throw new ProtocolException("JSON_INVALID","v2 encode failed",e);}
    }

    public ChatV2Message decode(byte[] raw)throws ProtocolException {
        if(raw==null||raw.length==0||raw.length>LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","v2 frame length invalid");
        String data;
        try {
            data=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        }catch(CharacterCodingException e){throw new ProtocolException("UTF8_INVALID","Invalid UTF-8",e);}
        try(JsonParser parser=mapper.getFactory().createParser(data)){
            JsonNode node=mapper.readTree(parser);
            if(node==null||!node.isObject()||parser.nextToken()!=null)
                throw new ProtocolException("JSON_INVALID","Expected one v2 object");
            fields(node,ROOT);
            if(!node.path("protocolVersion").isInt()||node.path("protocolVersion").intValue()!=2)
                throw new ProtocolException("VERSION_UNSUPPORTED","Not CHAT_V2");
            String name=required(node,"type");
            ChatV2Type type;
            try{type=ChatV2Type.valueOf(name);}
            catch(IllegalArgumentException e){throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE","Invalid v2 type");}
            JsonNode sender=object(node,"sender");fields(sender,SENDER);
            JsonNode dest=object(node,"destination");fields(dest,DEST);
            if(!"PEER".equals(required(dest,"kind")))
                throw new ProtocolException("JSON_INVALID","Expected PEER destination");
            JsonNode body=object(node,"payload");
            Map<String,Object> payload=new LinkedHashMap<>();
            Iterator<Map.Entry<String,JsonNode>> iter=body.fields();
            while(iter.hasNext()){
                var entry=iter.next();
                if(!entry.getValue().isTextual())
                    throw new ProtocolException("JSON_INVALID","Only bounded v2 text payload values allowed");
                payload.put(entry.getKey(),entry.getValue().textValue());
            }
            String timestamp=required(node,"sentAt");
            if(!timestamp.endsWith("Z"))throw new ProtocolException("JSON_INVALID","UTC timestamp expected");
            ChatV2Message msg=new ChatV2Message(uuid(node,"messageId"),type,
                uuid(node,"sessionId"),uuid(sender,"deviceId"),uuid(dest,"id"),
                uuid(node,"conversationId"),Instant.parse(timestamp),payload);
            validate(msg);
            return msg;
        }catch(ProtocolException e){throw e;}
        catch(Exception e){throw new ProtocolException("JSON_INVALID","Malformed v2 payload",e);}
    }

    private static void validate(ChatV2Message msg)throws ProtocolException {
        UUID reserved=new UUID(0,0);
        if(msg.messageId().equals(reserved)||msg.sessionId().equals(reserved)
            ||msg.senderId().equals(reserved)||msg.destinationId().equals(reserved)
            ||msg.conversationId().equals(reserved))
            throw new ProtocolException("JSON_INVALID","Zero UUID reserved; protected messages need real IDs");
        if(msg.senderId().equals(msg.destinationId()))
            throw new ProtocolException("IDENTITY_MISMATCH","Self addressed CHAT_V2 not supported");
        Map<String,Object> p=msg.payload();
        Set<String> allowed=switch(msg.type()){
            case CHAT_TEXT -> Set.of("text");
            case DELIVERY_ACK -> Set.of("originalMessageId","acceptedAtUTC","result");
            case READ_RECEIPT -> Set.of("originalMessageId","readEventId","viewedAtUTC");
            case CHAT_ERROR -> Set.of("errorCode","originalMessageId");
            case PAIR_CONFIRM -> Set.of("requestId","transcriptHash","confirmationId");
            case PAIR_READY -> Set.of("requestId","transcriptHash");
        };
        if(!allowed.containsAll(p.keySet()))
            throw new ProtocolException("JSON_INVALID","Unexpected v2 payload fields");
        if(!p.keySet().containsAll(switch(msg.type()){
            case CHAT_TEXT -> Set.of("text");
            case DELIVERY_ACK -> Set.of("originalMessageId","acceptedAtUTC","result");
            case READ_RECEIPT -> Set.of("originalMessageId","readEventId","viewedAtUTC");
            case CHAT_ERROR -> Set.of("errorCode");
            case PAIR_CONFIRM -> Set.of("requestId","transcriptHash","confirmationId");
            case PAIR_READY -> Set.of("requestId","transcriptHash");
        }))throw new ProtocolException("JSON_INVALID","Required payload field missing");
        for(var entry:p.entrySet())if(!(entry.getValue() instanceof String value)
                ||value.length()>ChatText.MAX_UTF8_BYTES)
            throw new ProtocolException("JSON_INVALID","Invalid payload field");
        try{
            switch(msg.type()){
                case CHAT_TEXT -> new ChatText((String)p.get("text"));
                case DELIVERY_ACK -> {
                    nonZeroUuid((String)p.get("originalMessageId"));
                    utcInstant((String)p.get("acceptedAtUTC"));
                    if(!"ACCEPTED".equals(p.get("result")))
                        throw new IllegalArgumentException("Invalid ACK result");
                }
                case READ_RECEIPT -> {
                    nonZeroUuid((String)p.get("originalMessageId"));
                    nonZeroUuid((String)p.get("readEventId"));
                    utcInstant((String)p.get("viewedAtUTC"));
                }
                case CHAT_ERROR -> {
                    String code=(String)p.get("errorCode");
                    if(code.isBlank()||code.length()>64||!code.matches("[A-Z_]+"))
                        throw new IllegalArgumentException("Invalid error code");
                    if(p.containsKey("originalMessageId"))nonZeroUuid((String)p.get("originalMessageId"));
                }
                case PAIR_CONFIRM, PAIR_READY -> {
                    nonZeroUuid((String)p.get("requestId"));
                    String hash=(String)p.get("transcriptHash");
                    if(!hash.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Invalid transcript");
                    if(msg.type()==ChatV2Type.PAIR_CONFIRM)nonZeroUuid((String)p.get("confirmationId"));
                }
            }
        }catch(IllegalArgumentException|NullPointerException invalid){
            throw new ProtocolException("JSON_INVALID","Invalid type-specific v2 payload",invalid);
        }
    }
    private static UUID nonZeroUuid(String text) {
        UUID result=UUID.fromString(text);
        if(result.equals(new UUID(0,0)))throw new IllegalArgumentException("Zero UUID reserved");
        return result;
    }
    private static Instant utcInstant(String text) {
        if(text==null||!text.endsWith("Z"))
            throw new IllegalArgumentException("UTC Z timestamp required");
        return Instant.parse(text);
    }
    private static void fields(JsonNode node,Set<String> allowed)throws ProtocolException{
        Iterator<String> names=node.fieldNames();
        while(names.hasNext())if(!allowed.contains(names.next()))
            throw new ProtocolException("JSON_INVALID","Unexpected v2 field");
    }
    private static JsonNode object(JsonNode node,String key)throws ProtocolException{
        JsonNode value=node.get(key);
        if(value==null||!value.isObject())throw new ProtocolException("JSON_INVALID","Missing "+key);
        return value;
    }
    private static String required(JsonNode node,String key)throws ProtocolException{
        JsonNode value=node.get(key);
        if(value==null||!value.isTextual()||value.textValue().isBlank())
            throw new ProtocolException("JSON_INVALID","Missing "+key);
        return value.textValue();
    }
    private static UUID uuid(JsonNode node,String key)throws ProtocolException{
        String value=required(node,key);
        if(!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ProtocolException("JSON_INVALID","Invalid UUID "+key);
        try{return UUID.fromString(value);}
        catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid UUID "+key,e);}
    }
}
