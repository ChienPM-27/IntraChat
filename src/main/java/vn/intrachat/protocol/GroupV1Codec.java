package vn.intrachat.protocol;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import java.io.IOException;
import vn.intrachat.domain.ChatText;

/**
 * Group-v1 parser/encoder for explicitly negotiated CHAT_V3_GROUP READY TLS sessions ONLY.
 * Decoding alone does NOT authenticate the claimed sender/owner or authorize membership.
 * This codec is deliberately not wired into legacy M1/v2 transport.
 */
public final class GroupV1Codec {
    public static final int LIMIT=65_536;
    private static final Set<String> ROOT=Set.of(
        "protocolVersion","feature","messageId","type","sessionId","sender",
        "destination","groupId","membershipRevision","sentAtUTC","payload");
    private static final Set<String> SENDER=Set.of("deviceId");
    private static final Set<String> DEST=Set.of("kind","id");
    private static final Map<GroupV1Type,Set<String>> REQUIRED=Map.ofEntries(
        Map.entry(GroupV1Type.GROUP_INVITE,Set.of("inviteId","ownerId","ownerSpkiSha256","title","expiresAtUTC","proposalHash")),
        Map.entry(GroupV1Type.GROUP_INVITE_DECISION,Set.of("inviteId","decision","proposalHash")),
        Map.entry(GroupV1Type.GROUP_ROSTER_COMMIT,Set.of("ownerId","rosterCanonicalBase64","ownerSignatureBase64")),
        Map.entry(GroupV1Type.GROUP_ROSTER_ACK,Set.of("rosterHash","result","commitEventId")),
        Map.entry(GroupV1Type.GROUP_LEAVE_REQUEST,Set.of("requestId","baseRevision")),
        Map.entry(GroupV1Type.GROUP_GRANT_REQUEST,Set.of("originalMessageId","senderId","rosterHash","recipientIds")),
        Map.entry(GroupV1Type.GROUP_SEND_GRANT,Set.of("grantCanonicalBase64","ownerSignatureBase64")),
        Map.entry(GroupV1Type.GROUP_TEXT,Set.of("text","grantCanonicalBase64","ownerSignatureBase64")),
        Map.entry(GroupV1Type.GROUP_DELIVERY_ACK,Set.of("originalMessageId","acceptedAtUTC","result")),
        Map.entry(GroupV1Type.GROUP_READ_RECEIPT,Set.of("originalMessageId","readEventId","viewedAtUTC")),
        Map.entry(GroupV1Type.GROUP_ERROR,Set.of("originalMessageId","errorCode"))
    );
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12)
            .maxStringLength(LIMIT).maxNumberLength(24).build()).build());

    public byte[] encode(GroupV1Message msg)throws ProtocolException{
        validate(msg);
        ObjectNode root=mapper.createObjectNode();
        root.put("protocolVersion",3);root.put("feature","GROUP_V1");
        root.put("messageId",msg.messageId().toString());
        root.put("type",msg.type().name());
        root.put("sessionId",msg.sessionId().toString());
        root.putObject("sender").put("deviceId",msg.senderId().toString());
        ObjectNode dest=root.putObject("destination");
        dest.put("kind","PEER");dest.put("id",msg.destinationId().toString());
        root.put("groupId",msg.groupId().toString());
        root.put("membershipRevision",msg.membershipRevision());
        root.put("sentAtUTC",msg.sentAt().toString());
        root.set("payload",mapper.valueToTree(msg.payload()));
        try{
            byte[] raw=mapper.writeValueAsBytes(root);
            if(raw.length==0||raw.length>LIMIT)throw new ProtocolException("FRAME_INVALID_LENGTH","Group frame too large");
            return raw;
        }catch(IOException e){throw new ProtocolException("JSON_INVALID","Group encode failed",e);}
    }
    public GroupV1Message decode(byte[] raw)throws ProtocolException {
        if(raw==null||raw.length==0||raw.length>LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","Group frame length invalid");
        String json;
        try{
            json=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        }catch(CharacterCodingException e){throw new ProtocolException("UTF8_INVALID","Group invalid UTF8",e);}
        try(JsonParser parser=mapper.getFactory().createParser(json)){
            JsonNode node=mapper.readTree(parser);
            if(node==null||!node.isObject()||parser.nextToken()!=null)
                throw new ProtocolException("JSON_INVALID","Expected one Group JSON object");
            checkKeys(node,ROOT);
            if(!node.path("protocolVersion").isInt()||node.path("protocolVersion").intValue()!=3
                    ||!"GROUP_V1".equals(text(node,"feature")))
                throw new ProtocolException("VERSION_UNSUPPORTED","Group V1 v3 profile required");
            GroupV1Type type;
            try{type=GroupV1Type.valueOf(text(node,"type"));}
            catch(IllegalArgumentException e){throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE","Invalid Group type");}
            JsonNode sender=object(node,"sender");checkKeys(sender,SENDER);
            JsonNode dest=object(node,"destination");checkKeys(dest,DEST);
            if(!"PEER".equals(text(dest,"kind")))throw new ProtocolException("JSON_INVALID","Group direct recipient must be PEER");
            JsonNode body=object(node,"payload");
            Set<String> required=REQUIRED.get(type);
            checkKeys(body,required);
            if(body.size()!=required.size())
                throw new ProtocolException("JSON_INVALID","Missing Group type-specific payload field");
            Map<String,Object> payload=new LinkedHashMap<>();
            Iterator<Map.Entry<String,JsonNode>> it=body.fields();
            while(it.hasNext()){
                var entry=it.next();
                JsonNode v=entry.getValue();
                if(v.isTextual())payload.put(entry.getKey(),v.textValue());
                else if("recipientIds".equals(entry.getKey())&&v.isArray()){
                    List<String> recipients=new ArrayList<>();
                    for(JsonNode element:v){
                        if(!element.isTextual())throw new ProtocolException("JSON_INVALID","Group recipient list must be strings");
                        recipients.add(element.textValue());
                    }
                    payload.put(entry.getKey(),List.copyOf(recipients));
                }else throw new ProtocolException("JSON_INVALID","Invalid group payload value");
            }
            JsonNode revision=node.get("membershipRevision");
            if(revision==null||!revision.isIntegralNumber()||!revision.canConvertToLong())
                throw new ProtocolException("JSON_INVALID","Invalid Group revision");
            GroupV1Message result=new GroupV1Message(uuid(node,"messageId"),type,
                uuid(node,"sessionId"),uuid(sender,"deviceId"),uuid(dest,"id"),
                uuid(node,"groupId"),revision.longValue(),utc(text(node,"sentAtUTC")),payload);
            validate(result);
            return result;
        }catch(ProtocolException e){throw e;}
        catch(Exception e){throw new ProtocolException("JSON_INVALID","Malformed Group frame",e);}
    }
    /**
     * Must be called only with real identifiers extracted from VERIFIED READY TLS session,
     * after GROUP_V1 capability negotiation. Claimed message fields cannot substitute TLS trust.
     */
    public void requireSessionBinding(GroupV1Message msg,UUID realSessionId,UUID tlsPeerId,
                                     UUID localDeviceId)throws ProtocolException {
        Objects.requireNonNull(msg);
        if(!msg.sessionId().equals(realSessionId)||!msg.senderId().equals(tlsPeerId)
            ||!msg.destinationId().equals(localDeviceId))
            throw new ProtocolException("IDENTITY_MISMATCH","Group session identity mismatch");
    }
    private static void validate(GroupV1Message msg)throws ProtocolException{
        try{
            ensureUuid(msg.messageId());ensureUuid(msg.sessionId());ensureUuid(msg.groupId());
            ensureUuid(msg.senderId());ensureUuid(msg.destinationId());
            if(msg.senderId().equals(msg.destinationId()))
                throw new IllegalArgumentException("Self destination forbidden");
            if(msg.membershipRevision()<0 || (msg.membershipRevision()==0
                && msg.type()!=GroupV1Type.GROUP_INVITE&&msg.type()!=GroupV1Type.GROUP_INVITE_DECISION))
                throw new IllegalArgumentException("Group revision must be committed");
            if(msg.sentAt().isBefore(Instant.EPOCH))
                throw new IllegalArgumentException("Invalid timestamp");
            Set<String> req=REQUIRED.get(msg.type());
            if(req==null||!msg.payload().keySet().equals(req))
                throw new IllegalArgumentException("Unexpected or missing Group fields");
            Map<String,Object> p=msg.payload();
            for(var e:p.entrySet()){
                Object val=e.getValue();
                if(val instanceof String s){
                    if(s.isEmpty()||s.length()>LIMIT)
                        throw new IllegalArgumentException("Invalid Group payload length");
                }else if(!"recipientIds".equals(e.getKey())||!(val instanceof List<?>))
                    throw new IllegalArgumentException("Unsupported Group payload kind");
            }
            switch(msg.type()){
                case GROUP_INVITE -> {
                    ensureUuid((String)p.get("inviteId"));ensureUuid((String)p.get("ownerId"));
                    hex32((String)p.get("ownerSpkiSha256"));hex32((String)p.get("proposalHash"));
                    String title=(String)p.get("title");
                    if(title.isBlank()||title.codePointCount(0,title.length())>60||title.getBytes(StandardCharsets.UTF_8).length>240
                        ||title.codePoints().anyMatch(Character::isISOControl))
                        throw new IllegalArgumentException("Invalid Group title");
                    utc((String)p.get("expiresAtUTC"));
                }
                case GROUP_INVITE_DECISION -> {
                    ensureUuid((String)p.get("inviteId"));hex32((String)p.get("proposalHash"));
                    if(!Set.of("ACCEPT","REJECT").contains(p.get("decision")))
                        throw new IllegalArgumentException("Invalid Group invite decision");
                }
                case GROUP_ROSTER_COMMIT -> {
                    ensureUuid((String)p.get("ownerId"));
                    boundedBase64((String)p.get("rosterCanonicalBase64"),1,4096);
                    boundedBase64((String)p.get("ownerSignatureBase64"),64,128);
                }
                case GROUP_ROSTER_ACK -> {
                    hex32((String)p.get("rosterHash"));ensureUuid((String)p.get("commitEventId"));
                    if(!Set.of("APPLIED","REJECTED").contains(p.get("result")))
                        throw new IllegalArgumentException("Invalid roster ACK result");
                }
                case GROUP_LEAVE_REQUEST -> {
                    ensureUuid((String)p.get("requestId"));
                    long base=Long.parseLong((String)p.get("baseRevision"));
                    if(base<1)throw new IllegalArgumentException("Invalid base revision");
                }
                case GROUP_GRANT_REQUEST -> {
                    ensureUuid((String)p.get("originalMessageId"));
                    if(!ensureUuid((String)p.get("senderId")).equals(msg.senderId()))
                        throw new IllegalArgumentException("Grant sender mismatch");
                    hex32((String)p.get("rosterHash"));
                    if(!(p.get("recipientIds") instanceof List<?> ids)||ids.size()<1||ids.size()>4)
                        throw new IllegalArgumentException("Grant recipient count");
                    Set<UUID> seen=new HashSet<>();
                    for(Object id:ids){
                        if(!(id instanceof String)||!seen.add(ensureUuid((String)id))||msg.senderId().equals(UUID.fromString((String)id)))
                            throw new IllegalArgumentException("Invalid recipient");
                    }
                }
                case GROUP_SEND_GRANT -> {
                    boundedBase64((String)p.get("grantCanonicalBase64"),1,4096);
                    boundedBase64((String)p.get("ownerSignatureBase64"),64,128);
                }
                case GROUP_TEXT -> {
                    new ChatText((String)p.get("text"));
                    boundedBase64((String)p.get("grantCanonicalBase64"),1,4096);
                    boundedBase64((String)p.get("ownerSignatureBase64"),64,128);
                }
                case GROUP_DELIVERY_ACK -> {
                    ensureUuid((String)p.get("originalMessageId"));utc((String)p.get("acceptedAtUTC"));
                    if(!"ACCEPTED".equals(p.get("result")))throw new IllegalArgumentException("Invalid ACK");
                }
                case GROUP_READ_RECEIPT -> {
                    ensureUuid((String)p.get("originalMessageId"));ensureUuid((String)p.get("readEventId"));
                    utc((String)p.get("viewedAtUTC"));
                }
                case GROUP_ERROR -> {
                    ensureUuid((String)p.get("originalMessageId"));
                    String code=(String)p.get("errorCode");
                    if(code.length()>64||!code.matches("[A-Z_]+"))
                        throw new IllegalArgumentException("Invalid safe code");
                }
            }
        }catch(Exception e){
            if(e instanceof ProtocolException pe)throw pe;
            throw new ProtocolException("JSON_INVALID","Invalid type-specific Group data",e);
        }
    }
    private static void boundedBase64(String raw,int min,int max){
        byte[] d=Base64.getDecoder().decode(raw);
        if(d.length<min||d.length>max)throw new IllegalArgumentException("Group Base64 component invalid");
    }
    private static void hex32(String s){
        if(s==null||!s.matches("(?i)[0-9a-f]{64}"))throw new IllegalArgumentException("Expected SPKI/hash hex32");
    }
    private static UUID ensureUuid(String value){
        if(value==null||!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid UUID format");
        return ensureUuid(UUID.fromString(value));
    }
    private static UUID ensureUuid(UUID value){
        Objects.requireNonNull(value);
        if(value.equals(new UUID(0,0)))throw new IllegalArgumentException("Zero UUID reserved");
        return value;
    }
    private static Instant utc(String raw){
        if(raw==null||!raw.endsWith("Z"))throw new IllegalArgumentException("UTC Z required");
        return Instant.parse(raw);
    }
    private static JsonNode object(JsonNode obj,String key)throws ProtocolException{
        JsonNode n=obj.get(key);
        if(n==null||!n.isObject())throw new ProtocolException("JSON_INVALID","Missing Group object");
        return n;
    }
    private static String text(JsonNode obj,String key)throws ProtocolException{
        JsonNode n=obj.get(key);
        if(n==null||!n.isTextual()||n.textValue().isBlank())
            throw new ProtocolException("JSON_INVALID","Missing Group text field");
        return n.textValue();
    }
    private static UUID uuid(JsonNode obj,String key)throws ProtocolException{
        try{return ensureUuid(text(obj,key));}
        catch(IllegalArgumentException e){throw new ProtocolException("JSON_INVALID","Invalid Group UUID",e);}
    }
    private static void checkKeys(JsonNode node,Set<String> allowed)throws ProtocolException{
        var names=node.fieldNames();
        while(names.hasNext())if(!allowed.contains(names.next()))
            throw new ProtocolException("JSON_INVALID","Unexpected Group field");
    }
}
