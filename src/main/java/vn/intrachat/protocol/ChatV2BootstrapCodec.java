package vn.intrachat.protocol;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Untrusted, bounded, data-free CHAT_V2 first preface. NEVER carries user text. */
public final class ChatV2BootstrapCodec {
    public static final int LIMIT=16_384;
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
            .maxStringLength(512).maxNumberLength(24).build()).build());
    public record Hello(UUID requestId,UUID senderId,String displayName,byte[] nonce) {
        public Hello {
            Objects.requireNonNull(requestId);Objects.requireNonNull(senderId);
            if(requestId.equals(new UUID(0,0))||senderId.equals(new UUID(0,0)))
                throw new IllegalArgumentException("Zero request/device UUID is reserved");
            if(displayName==null||displayName.isBlank()||displayName.length()>128)
                throw new IllegalArgumentException("Invalid display name");
            if(nonce==null||nonce.length!=32)throw new IllegalArgumentException("Requires 32-byte nonce");
            nonce=nonce.clone();
        }
        @Override public byte[] nonce(){return nonce.clone();}
    }
    public byte[] encodeHello(Hello hello)throws ProtocolException{
        Objects.requireNonNull(hello);
        ObjectNode root=mapper.createObjectNode();
        root.put("prefaceVersion",2);
        root.put("type","CHAT_HELLO");
        root.put("requestId",hello.requestId().toString());
        ObjectNode sender=root.putObject("sender");
        sender.put("deviceId",hello.senderId().toString());
        sender.put("displayName",hello.displayName());
        root.put("targetDeviceId","00000000-0000-0000-0000-000000000000");
        root.put("senderNonce",Base64.getEncoder().encodeToString(hello.nonce()));
        root.putArray("capabilities").add("CHAT_TLS13_V2");
        try{
            byte[] raw=mapper.writeValueAsBytes(root);
            if(raw.length>LIMIT)throw new ProtocolException("FRAME_INVALID_LENGTH","Oversized CHAT_HELLO");
            return raw;
        }catch(IOException e){throw new ProtocolException("JSON_INVALID","Cannot encode CHAT_HELLO",e);}
    }
    public Hello decodeHello(byte[] raw)throws ProtocolException{
        if(raw==null||raw.length==0||raw.length>LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","Invalid preface length");
        final String json;
        try{json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();}
        catch(CharacterCodingException e){throw new ProtocolException("UTF8_INVALID","Invalid preface UTF8",e);}
        try(JsonParser parser=mapper.getFactory().createParser(json)){
            JsonNode node=mapper.readTree(parser);
            if(node==null||!node.isObject()||parser.nextToken()!=null)
                throw new ProtocolException("JSON_INVALID","Single preface object required");
            Set<String> fields=Set.of("prefaceVersion","type","requestId","sender","targetDeviceId","senderNonce","capabilities");
            var names=node.fieldNames();
            while(names.hasNext())if(!fields.contains(names.next()))
                throw new ProtocolException("JSON_INVALID","Unknown preface key");
            if(!node.path("prefaceVersion").isInt()||node.path("prefaceVersion").intValue()!=2)
                throw new ProtocolException("VERSION_UNSUPPORTED","Expected CHAT_V2");
            if(!"CHAT_HELLO".equals(required(node,"type")))
                throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE","Expected CHAT_HELLO");
            UUID requestId=uuid(node,"requestId");
            JsonNode sender=node.get("sender");
            if(sender==null||!sender.isObject()||sender.size()!=2
                    ||sender.get("deviceId")==null||sender.get("displayName")==null)
                throw new ProtocolException("JSON_INVALID","Invalid sender");
            UUID senderId=uuid(sender,"deviceId");
            String name=required(sender,"displayName");
            if(!"00000000-0000-0000-0000-000000000000".equals(required(node,"targetDeviceId")))
                throw new ProtocolException("JSON_INVALID","Unknown peer sentinel required");
            JsonNode capabilities=node.get("capabilities");
            if(capabilities==null||!capabilities.isArray()||capabilities.size()!=1
                    ||!capabilities.get(0).isTextual()
                    ||!"CHAT_TLS13_V2".equals(capabilities.get(0).textValue()))
                throw new ProtocolException("JSON_INVALID","Unsupported capabilities");
            byte[] nonce=Base64.getDecoder().decode(required(node,"senderNonce"));
            return new Hello(requestId,senderId,name,nonce);
        }catch(ProtocolException e){throw e;}
        catch(Exception e){throw new ProtocolException("JSON_INVALID","Invalid v2 HELLO",e);}
    }
    private static String required(JsonNode node,String name)throws ProtocolException{
        JsonNode value=node.get(name);
        if(value==null||!value.isTextual()||value.textValue().isBlank())
            throw new ProtocolException("JSON_INVALID","Missing "+name);
        return value.textValue();
    }
    private static UUID uuid(JsonNode node,String name)throws ProtocolException{
        String raw=required(node,name);
        if(!raw.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new ProtocolException("JSON_INVALID","Invalid "+name);
        UUID parsed=UUID.fromString(raw);
        if(parsed.equals(new UUID(0,0)))throw new ProtocolException("JSON_INVALID","Zero UUID is reserved for unknown target only");
        return parsed;
    }

    public record Accept(UUID requestId,UUID receiverId,String displayName,byte[] nonce) {
        public Accept {
            Objects.requireNonNull(requestId);Objects.requireNonNull(receiverId);
            if(requestId.equals(new UUID(0,0))||receiverId.equals(new UUID(0,0)))
                throw new IllegalArgumentException("Zero request/device UUID is reserved");
            if(displayName==null||displayName.isBlank()||displayName.length()>128)
                throw new IllegalArgumentException("Invalid receiver displayName");
            if(nonce==null||nonce.length!=32)throw new IllegalArgumentException("Expected 32-byte nonce");
            nonce=nonce.clone();
        }
        @Override public byte[] nonce(){return nonce.clone();}
    }
    public byte[] encodeAccept(Accept accept)throws ProtocolException {
        ObjectNode root=mapper.createObjectNode();
        root.put("prefaceVersion",2);root.put("type","CHAT_ACCEPT");
        root.put("requestId",accept.requestId().toString());
        ObjectNode sender=root.putObject("sender");
        sender.put("deviceId",accept.receiverId().toString());
        sender.put("displayName",accept.displayName());
        root.put("receiverNonce",Base64.getEncoder().encodeToString(accept.nonce()));
        try {
            byte[] raw=mapper.writeValueAsBytes(root);
            if(raw.length>LIMIT)throw new ProtocolException("FRAME_INVALID_LENGTH","CHAT_ACCEPT exceeds limit");
            return raw;
        }catch(IOException e){throw new ProtocolException("JSON_INVALID","Cannot encode accept",e);}
    }
    public Accept decodeAccept(byte[] raw)throws ProtocolException {
        JsonNode node=parseResponse(raw);
        if(!node.path("prefaceVersion").isInt()||node.path("prefaceVersion").intValue()!=2)
            throw new ProtocolException("VERSION_UNSUPPORTED","Not v2 response");
        if("CHAT_REJECT".equals(node.path("type").asText()))
            throw new ProtocolException("USER_REJECTED","Receiving person declined secure chat");
        if(!"CHAT_ACCEPT".equals(required(node,"type")))
            throw new ProtocolException("HANDSHAKE_INVALID","Expected CHAT_ACCEPT");
        if(node.size()!=5)throw new ProtocolException("JSON_INVALID","Unexpected accept fields");
        JsonNode sender=node.get("sender");
        if(sender==null||!sender.isObject()||sender.size()!=2)
            throw new ProtocolException("JSON_INVALID","Invalid accept identity");
        try {return new Accept(uuid(node,"requestId"),uuid(sender,"deviceId"),
            required(sender,"displayName"),Base64.getDecoder().decode(required(node,"receiverNonce")));}
        catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid accept nonce",e);}
    }
    public byte[] encodeReject(UUID requestId)throws ProtocolException {
        ObjectNode root=mapper.createObjectNode();
        root.put("prefaceVersion",2);root.put("type","CHAT_REJECT");
        root.put("requestId",Objects.requireNonNull(requestId).toString());
        try{return mapper.writeValueAsBytes(root);}
        catch(IOException e){throw new ProtocolException("JSON_INVALID","Cannot encode rejection",e);}
    }
    private JsonNode parseResponse(byte[] raw)throws ProtocolException{
        if(raw==null||raw.length==0||raw.length>LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","Invalid response length");
        try {
            String data=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString();
            try(JsonParser parser=mapper.getFactory().createParser(data)){
                JsonNode node=mapper.readTree(parser);
                if(node==null||!node.isObject()||parser.nextToken()!=null)
                    throw new ProtocolException("JSON_INVALID","Response must be one object");
                return node;
            }
        }catch(ProtocolException e){throw e;}
        catch(Exception e){throw new ProtocolException("JSON_INVALID","Invalid response JSON",e);}
    }
}
