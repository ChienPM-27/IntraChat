package vn.intrachat.protocol;

/** Protected CHAT_V2 application types ONLY; no v1 diagnostics or future Group/File/History. */
public enum ChatV2Type {
    PAIR_CONFIRM, PAIR_READY, CHAT_TEXT, DELIVERY_ACK, READ_RECEIPT, CHAT_ERROR
}
