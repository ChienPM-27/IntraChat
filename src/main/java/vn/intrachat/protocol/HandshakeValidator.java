package vn.intrachat.protocol;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Semantic requirements for preapproval HELLO and its request identifier. */
public final class HandshakeValidator {
    private HandshakeValidator() {}

    public static UUID validateHello(NetworkEnvelope message) throws ProtocolException {
        if (message.type() != MessageType.HELLO
                || !NetworkEnvelope.UNKNOWN_PEER.equals(message.destination().id())) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Expected bootstrap HELLO");
        }
        Map<String, Object> body = message.payload();
        Object versions = body.get("supportedVersions");
        if (!(versions instanceof List<?> list) || list.size() != 1
                || !(list.getFirst() instanceof Number version)
                || version.intValue() != 1) {
            throw new ProtocolException("VERSION_UNSUPPORTED", "No common protocol version");
        }
        Object clientVersion = body.get("clientVersion");
        if (!(clientVersion instanceof String client) || client.isBlank() || client.length() > 48) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Invalid client version");
        }
        return requireUuid(body, "requestId");
    }

    public static UUID requireUuid(Map<String, Object> payload, String field) throws ProtocolException {
        Object value = payload.get(field);
        if (!(value instanceof String text) || !text.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Invalid or missing " + field);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            throw new ProtocolException("HANDSHAKE_INVALID", "Invalid " + field, invalid);
        }
    }
}
