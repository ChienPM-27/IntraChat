package vn.intrachat.protocol;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Strict, data-free CHAT_V3_GROUP HELLO/ACCEPT preface.
 * NOT enabled on existing M1/v2 listeners; user content forbidden before mutual TLS
 * and two-sided v3 pairing transcript confirmation.
 */
public final class GroupV3BootstrapCodec {
    public static final int LIMIT=16_384;
    private static final String ZERO="00000000-0000-0000-0000-000000000000";
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
            .maxStringLength(512).maxNumberLength(24).build()).build());

    public record Hello(UUID requestId,UUID senderId,String displayName,byte[] nonce) {
        public Hello{validate(requestId,senderId,displayName,nonce);nonce=nonce.clone();}
        @Override public byte[] nonce(){return nonce.clone();}
    }
    public record Accept(UUID requestId,UUID receiverId,String displayName,byte[] nonce) {
        public Accept{validate(requestId,receiverId,displayName,nonce);nonce=nonce.clone();}
        @Override public byte[] nonce(){return nonce.clone();}
    }
    private static void validate(UUID request,UUID peer,String name,byte[] nonce){
        if(request==null||peer==null||request.equals(new UUID(0,0))||peer.equals(new UUID(0,0)))
            throw new IllegalArgumentException("Invalid v3 nonzero UUID");
        if(name==null||name.isBlank()||name.length()>128||name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid display name");
        if(nonce==null||nonce.length!=32)throw new IllegalArgumentException("Invalid V3 nonce");
    }
    public byte[] encodeHello(Hello hello)throws ProtocolException{
        Objects.requireNonNull(hello);
        ObjectNode node=base("CHAT_HELLO",hello.requestId(),hello.senderId(),hello.displayName());
        node.put("targetDeviceId",ZERO);
        node.put("senderNonce",Base64.getEncoder().encodeToString(hello.nonce()));
        array(node,"capabilities");
        return bytes(node);
    }
    public Hello decodeHello(byte[] raw)throws ProtocolException {
        JsonNode node=parse(raw,Set.of("prefaceVersion","type","requestId","sender","targetDeviceId","senderNonce","capabilities"));
        if(!"CHAT_HELLO".equals(text(node,"type"))||!ZERO.equals(text(node,"targetDeviceId")))
            throw new ProtocolException("HANDSHAKE_INVALID","Wrong V3 HELLO type/target");
        capabilities(node,"capabilities");
        try{return new Hello(uuid(node,"requestId"),uuid(object(node,"sender"),"deviceId"),
            text(object(node,"sender"),"displayName"),nonce(node,"senderNonce"));}
        catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid V3 HELLO",e);}
    }
    public byte[] encodeAccept(Accept accept)throws ProtocolException{
        Objects.requireNonNull(accept);
        ObjectNode node=base("CHAT_ACCEPT",accept.requestId(),accept.receiverId(),accept.displayName());
        node.put("receiverNonce",Base64.getEncoder().encodeToString(accept.nonce()));
        array(node,"selectedCapabilities");
        return bytes(node);
    }
    public Accept decodeAccept(byte[] raw)throws ProtocolException{
        JsonNode node=parse(raw,Set.of("prefaceVersion","type","requestId","sender","receiverNonce","selectedCapabilities"));
        if(!"CHAT_ACCEPT".equals(text(node,"type")))
            throw new ProtocolException("HANDSHAKE_INVALID","Expected v3 accept");
        capabilities(node,"selectedCapabilities");
        try{return new Accept(uuid(node,"requestId"),uuid(object(node,"sender"),"deviceId"),
            text(object(node,"sender"),"displayName"),nonce(node,"receiverNonce"));}
        catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid V3 ACCEPT",e);}
    }
    private ObjectNode base(String type,UUID requestId,UUID senderId,String name){
        ObjectNode node=mapper.createObjectNode();
        node.put("prefaceVersion",3);node.put("type",type);
        node.put("requestId",requestId.toString());
        ObjectNode sender=node.putObject("sender");
        sender.put("deviceId",senderId.toString());sender.put("displayName",name);
        return node;
    }
    private static void array(ObjectNode node,String key){
        node.putArray(key).add("CHAT_TLS13_V2").add("GROUP_V1");
    }
    private byte[] bytes(ObjectNode node)throws ProtocolException{
        try{
            byte[] result=mapper.writeValueAsBytes(node);
            if(result.length==0||result.length>LIMIT)
                throw new ProtocolException("FRAME_INVALID_LENGTH","Invalid V3 preface size");
            return result;
        }catch(IOException e){throw new ProtocolException("JSON_INVALID","Cannot encode V3 preface",e);}
    }
    private JsonNode parse(byte[] raw,Set<String> names)throws ProtocolException{
        if(raw==null||raw.length<1||raw.length>LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","Invalid V3 preface size");
        String json;
        try{json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
        catch(CharacterCodingException e){throw new ProtocolException("UTF8_INVALID","Invalid V3 UTF8",e);}
        try(JsonParser parser=mapper.getFactory().createParser(json)){
            JsonNode node=mapper.readTree(parser);
            if(node==null||!node.isObject()||parser.nextToken()!=null)
                throw new ProtocolException("JSON_INVALID","Expected exactly one V3 preface");
            if(node.size()!=names.size())throw new ProtocolException("JSON_INVALID","Missing V3 preface fields");
            var it=node.fieldNames();
            while(it.hasNext())if(!names.contains(it.next()))
                throw new ProtocolException("JSON_INVALID","Unknown V3 preface field");
            if(!node.path("prefaceVersion").isInt()||node.path("prefaceVersion").intValue()!=3)
                throw new ProtocolException("VERSION_UNSUPPORTED","CHAT_V3_GROUP required");
            JsonNode sender=object(node,"sender");
            if(sender.size()!=2||!sender.has("deviceId")||!sender.has("displayName"))
                throw new ProtocolException("JSON_INVALID","Invalid V3 sender object");
            return node;
        }catch(ProtocolException e){throw e;}
        catch(Exception e){throw new ProtocolException("JSON_INVALID","Malformed V3 preface",e);}
    }
    private static void capabilities(JsonNode node,String field)throws ProtocolException{
        JsonNode caps=node.get(field);
        if(caps==null||!caps.isArray()||caps.size()!=2
            ||!"CHAT_TLS13_V2".equals(caps.get(0).asText())
            ||!"GROUP_V1".equals(caps.get(1).asText()))
            throw new ProtocolException("VERSION_UNSUPPORTED","Exact V3 Group capability list required");
    }
    private static JsonNode object(JsonNode node,String key)throws ProtocolException{
        JsonNode n=node.get(key);
        if(n==null||!n.isObject())throw new ProtocolException("JSON_INVALID","V3 sender object required");
        return n;
    }
    private static String text(JsonNode node,String key)throws ProtocolException{
        JsonNode n=node.get(key);
        if(n==null||!n.isTextual()||n.textValue().isEmpty())
            throw new ProtocolException("JSON_INVALID","V3 required text missing");
        return n.textValue();
    }
    private static UUID uuid(JsonNode node,String key)throws ProtocolException{
        String s=text(node,key);
        if(!s.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ProtocolException("JSON_INVALID","Invalid V3 UUID");
        UUID id=UUID.fromString(s);
        if(id.equals(new UUID(0,0)))throw new ProtocolException("JSON_INVALID","Zero V3 UUID forbidden");
        return id;
    }
    private static byte[] nonce(JsonNode node,String key)throws ProtocolException{
        try{
            byte[] nonce=Base64.getDecoder().decode(text(node,key));
            if(nonce.length!=32)throw new IllegalArgumentException();
            return nonce;
        }catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid V3 nonce",e);}
    }
}
