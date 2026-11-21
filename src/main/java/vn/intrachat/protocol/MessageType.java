package vn.intrachat.protocol;

/** Draft v1 vocabulary; no network or codec implementation exists in M0. */
public enum MessageType {
    HELLO, CHAT_TEXT, GROUP_MESSAGE, ACK, SYNC_SUMMARY, SYNC_REQUEST, SYNC_BATCH,
    FILE_OFFER, FILE_STATUS
}
