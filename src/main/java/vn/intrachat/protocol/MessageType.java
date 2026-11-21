package vn.intrachat.protocol;

/** Versioned message vocabulary. Only technical M1 types are enabled by JsonEnvelopeCodec. */
public enum MessageType {
    HELLO, HELLO_ACCEPT, HELLO_REJECT, HELLO_CONFIRM, HELLO_READY,
    DIAGNOSTIC_PING, DIAGNOSTIC_PONG, PROTOCOL_ERROR,
    CHAT_TEXT, GROUP_MESSAGE, ACK, SYNC_SUMMARY, SYNC_REQUEST, SYNC_BATCH,
    FILE_OFFER, FILE_STATUS
}
