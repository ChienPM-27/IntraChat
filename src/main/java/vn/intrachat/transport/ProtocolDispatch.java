package vn.intrachat.transport;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import vn.intrachat.protocol.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Objects;

/**
 * One bounded first-frame classifier for a manually started shared TCP listener.
 * There is NO fallback or retry across profiles: an invalid version, duplicate
 * field or unexpected type terminates the candidate. Diagnostics are plaintext
 * and never carry chat/group/file payload; v2 is upgraded to mutual TLS before
 * any user content can be processed.
 */
public final class ProtocolDispatch {
    public enum Profile { M1_DIAGNOSTICS, M2_TLS13, M3_GROUP_UNSUPPORTED }
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(10).maxStringLength(8192).maxNumberLength(64).build())
        .build());
    private final JsonEnvelopeCodec m1=new JsonEnvelopeCodec();
    private final ChatV2BootstrapCodec m2=new ChatV2BootstrapCodec();
    private final GroupV3BootstrapCodec m3=new GroupV3BootstrapCodec();

    public Profile inspect(byte[] firstFrame)throws ProtocolException {
        if(firstFrame==null||firstFrame.length==0||firstFrame.length>FrameCodec.HELLO_LIMIT)
            throw new ProtocolException("FRAME_INVALID_LENGTH","Invalid first frame size");
        final String text;
        try{
            text=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(firstFrame)).toString();
        }catch(CharacterCodingException invalid){
            throw new ProtocolException("UTF8_INVALID","Invalid bootstrap encoding",invalid);
        }
        try(JsonParser parser=mapper.getFactory().createParser(text)){
            JsonNode node=mapper.readTree(parser);
            if(node==null||!node.isObject()||parser.nextToken()!=null)
                throw new ProtocolException("JSON_INVALID","Expected one bootstrap object");
            boolean old=node.has("protocolVersion"), newer=node.has("prefaceVersion");
            if(old==newer)
                throw new ProtocolException("VERSION_UNSUPPORTED",
                    "Exactly one explicit supported protocol version required");
            if(old){
                if(!node.get("protocolVersion").isInt()||node.get("protocolVersion").intValue()!=1)
                    throw new ProtocolException("VERSION_UNSUPPORTED","Unsupported diagnostics profile");
                NetworkEnvelope hello=m1.decode(firstFrame);
                HandshakeValidator.validateHello(hello);
                return Profile.M1_DIAGNOSTICS;
            }
            if(!node.get("prefaceVersion").isInt())
                throw new ProtocolException("VERSION_UNSUPPORTED","Explicit preface integer required");
            return switch(node.get("prefaceVersion").intValue()){
                case 2 -> {m2.decodeHello(firstFrame);yield Profile.M2_TLS13;}
                case 3 -> {m3.decodeHello(firstFrame);yield Profile.M3_GROUP_UNSUPPORTED;}
                default -> throw new ProtocolException("VERSION_UNSUPPORTED","Unrecognized preface version");
            };
        }catch(ProtocolException expected){throw expected;}
        catch(Exception malformed){
            throw new ProtocolException("JSON_INVALID","Invalid bootstrap preface",malformed);
        }
    }
}
