package vn.intrachat.protocol;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import vn.intrachat.domain.PeerIdentity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict diagnostic-only JSON protocol v1. Remote input is always untrusted. */
public final class JsonEnvelopeCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
        "protocolVersion", "messageId", "type", "sender", "destination", "sentAt", "payload");
    private static final Set<String> SENDER_FIELDS = Set.of("deviceId", "displayName");
    private static final Set<String> DEST_FIELDS = Set.of("kind", "id");
    private static final EnumSet<MessageType> ALLOWED_TYPES = EnumSet.of(
        MessageType.HELLO, MessageType.HELLO_ACCEPT, MessageType.HELLO_REJECT,
        MessageType.HELLO_CONFIRM, MessageType.HELLO_READY,
        MessageType.DIAGNOSTIC_PING, MessageType.DIAGNOSTIC_PONG, MessageType.PROTOCOL_ERROR);
    private final ObjectMapper mapper;

    public JsonEnvelopeCodec() {
        JsonFactory factory = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32)
                .maxStringLength(8192)
                .maxNumberLength(64)
                .build())
            .build();
        mapper = new ObjectMapper(factory);
    }

    public byte[] encode(NetworkEnvelope envelope) throws ProtocolException {
        validate(envelope);
        ObjectNode root = mapper.createObjectNode();
        root.put("protocolVersion", envelope.protocolVersion());
        root.put("messageId", envelope.messageId().toString());
        root.put("type", envelope.type().name());
        ObjectNode sender = root.putObject("sender");
        sender.put("deviceId", envelope.sender().deviceId().toString());
        sender.put("displayName", envelope.sender().displayName());
        ObjectNode destination = root.putObject("destination");
        destination.put("kind", "PEER");
        destination.put("id", envelope.destination().id().toString());
        root.put("sentAt", envelope.sentAt().toString());
        root.set("payload", mapper.valueToTree(envelope.payload()));
        try {
            byte[] bytes = mapper.writeValueAsBytes(root);
            if (bytes.length > FrameCodec.CONTROL_LIMIT) {
                throw new ProtocolException("FRAME_INVALID_LENGTH", "Envelope exceeds control frame bound");
            }
            return bytes;
        } catch (IOException failure) {
            throw new ProtocolException("JSON_INVALID", "Could not encode envelope", failure);
        }
    }

    public NetworkEnvelope decode(byte[] raw) throws ProtocolException {
        if (raw == null || raw.length == 0 || raw.length > FrameCodec.CONTROL_LIMIT) {
            throw new ProtocolException("FRAME_INVALID_LENGTH", "Invalid JSON frame length");
        }
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException invalid) {
            throw new ProtocolException("UTF8_INVALID", "Invalid UTF-8 sequence", invalid);
        }
        try (JsonParser parser = mapper.getFactory().createParser(json)) {
            JsonNode root = mapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw new ProtocolException("JSON_INVALID", "Expected one JSON object");
            }
            fieldsOnly(root, ROOT_FIELDS);
            if (!root.path("protocolVersion").isInt() || root.path("protocolVersion").intValue() != 1) {
                throw new ProtocolException("VERSION_UNSUPPORTED", "Unsupported protocol version");
            }
            UUID messageId = requireUuid(root, "messageId");
            String typeName = requireText(root, "type");
            final MessageType type;
            try {
                type = MessageType.valueOf(typeName);
            } catch (IllegalArgumentException invalid) {
                throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE", "Unknown message type");
            }
            if (!ALLOWED_TYPES.contains(type)) {
                throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE", "Message type is unavailable in M1");
            }
            JsonNode senderNode = objectField(root, "sender");
            fieldsOnly(senderNode, SENDER_FIELDS);
            PeerIdentity sender = new PeerIdentity(
                requireUuid(senderNode, "deviceId"), requireText(senderNode, "displayName"));
            JsonNode destNode = objectField(root, "destination");
            fieldsOnly(destNode, DEST_FIELDS);
            if (!"PEER".equals(requireText(destNode, "kind"))) {
                throw new ProtocolException("JSON_INVALID", "M1 destination must be PEER");
            }
            Destination destination = new Destination(DestinationKind.PEER, requireUuid(destNode, "id"));
            Instant sentAt;
            try {
                sentAt = Instant.parse(requireText(root, "sentAt"));
            } catch (DateTimeParseException invalid) {
                throw new ProtocolException("JSON_INVALID", "Expected UTC ISO-8601 timestamp", invalid);
            }
            JsonNode body = objectField(root, "payload");
            Map<String,Object> payload = mapper.convertValue(body, new TypeReference<Map<String,Object>>() {});
            NetworkEnvelope envelope = new NetworkEnvelope(1, messageId, type, sender, destination, sentAt, payload);
            validate(envelope);
            return envelope;
        } catch (ProtocolException expected) {
            throw expected;
        } catch (Exception invalid) {
            throw new ProtocolException("JSON_INVALID", "Invalid JSON envelope", invalid);
        }
    }

    private void validate(NetworkEnvelope envelope) throws ProtocolException {
        if (!ALLOWED_TYPES.contains(envelope.type())) {
            throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE", "Only M1 technical messages are allowed");
        }
        if (envelope.sender().deviceId().equals(NetworkEnvelope.UNKNOWN_PEER)) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Sender device ID cannot be empty");
        }
        if (envelope.destination().kind() != DestinationKind.PEER) {
            throw new ProtocolException("JSON_INVALID", "Group messages are not supported in M1");
        }
        if (envelope.destination().id().equals(NetworkEnvelope.UNKNOWN_PEER)
                && envelope.type() != MessageType.HELLO) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Unknown peer destination allowed for HELLO only");
        }
        if (envelope.type() == MessageType.HELLO
                && !envelope.destination().id().equals(NetworkEnvelope.UNKNOWN_PEER)) {
            throw new ProtocolException("HANDSHAKE_INVALID", "HELLO requires bootstrap destination");
        }

        Map<String,Object> body = envelope.payload();
        switch (envelope.type()) {
            case HELLO -> {
                fieldsOnly(body, Set.of("requestId", "supportedVersions", "clientVersion"));
                HandshakeValidator.validateHello(envelope);
            }
            case HELLO_ACCEPT, HELLO_CONFIRM -> {
                fieldsOnly(body, Set.of("requestId", "sessionId", "chosenVersion"));
                HandshakeValidator.requireUuid(body, "requestId");
                HandshakeValidator.requireUuid(body, "sessionId");
                if (!(body.get("chosenVersion") instanceof Number version) || version.intValue() != 1) {
                    throw new ProtocolException("VERSION_UNSUPPORTED", "Invalid negotiated version");
                }
            }
            case HELLO_REJECT -> {
                fieldsOnly(body, Set.of("requestId", "reasonCode"));
                String code = requiredString(body, "reasonCode");
                if (code.length() > 64) throw new ProtocolException("JSON_INVALID", "Invalid reason");
                if (body.containsKey("requestId")) HandshakeValidator.requireUuid(body, "requestId");
            }
            case HELLO_READY -> {
                fieldsOnly(body, Set.of("sessionId"));
                HandshakeValidator.requireUuid(body, "sessionId");
            }
            case DIAGNOSTIC_PING, DIAGNOSTIC_PONG -> {
                fieldsOnly(body, Set.of("probeId", "nonce"));
                HandshakeValidator.requireUuid(body, "probeId");
                if (body.containsKey("nonce")) {
                    String nonce = requiredString(body, "nonce");
                    if (nonce.getBytes(StandardCharsets.UTF_8).length > 4096) {
                        throw new ProtocolException("FRAME_INVALID_LENGTH", "Diagnostic payload too large");
                    }
                }
            }
            case PROTOCOL_ERROR -> {
                fieldsOnly(body, Set.of("errorCode"));
                String code = requiredString(body, "errorCode");
                if (code.length() > 64) throw new ProtocolException("JSON_INVALID", "Invalid error code");
            }
            default -> throw new ProtocolException("UNSUPPORTED_TYPE_FOR_PHASE", "Unsupported message type");
        }
    }

    private static String requiredString(Map<String,Object> body, String name) throws ProtocolException {
        Object value = body.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ProtocolException("JSON_INVALID", "Missing or invalid " + name);
        }
        return text;
    }

    private static void fieldsOnly(Map<String,Object> actual, Set<String> allowed) throws ProtocolException {
        if (!allowed.containsAll(actual.keySet())) {
            throw new ProtocolException("JSON_INVALID", "Unexpected payload field");
        }
    }

    private static void fieldsOnly(JsonNode actual, Set<String> allowed) throws ProtocolException {
        Iterator<String> names = actual.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw new ProtocolException("JSON_INVALID", "Unexpected envelope field");
            }
        }
    }

    private static JsonNode objectField(JsonNode node, String key) throws ProtocolException {
        JsonNode child = node.get(key);
        if (child == null || !child.isObject()) {
            throw new ProtocolException("JSON_INVALID", "Missing or invalid " + key);
        }
        return child;
    }

    private static String requireText(JsonNode node, String key) throws ProtocolException {
        JsonNode child = node.get(key);
        if (child == null || !child.isTextual() || child.textValue().isBlank()) {
            throw new ProtocolException("JSON_INVALID", "Missing or invalid " + key);
        }
        return child.textValue();
    }

    private static UUID requireUuid(JsonNode node, String key) throws ProtocolException {
        String text = requireText(node, key);
        if (!text.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ProtocolException("JSON_INVALID", "Invalid UUID in " + key);
        }
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException invalid) {
            throw new ProtocolException("JSON_INVALID", "Invalid UUID in " + key, invalid);
        }
    }
}
