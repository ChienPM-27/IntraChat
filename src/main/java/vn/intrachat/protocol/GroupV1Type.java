package vn.intrachat.protocol;

/** Group user/control messages require positively negotiated CHAT_V3_GROUP over verified TLS. */
public enum GroupV1Type {
    GROUP_INVITE, GROUP_INVITE_DECISION, GROUP_ROSTER_COMMIT, GROUP_ROSTER_ACK,
    GROUP_LEAVE_REQUEST, GROUP_GRANT_REQUEST, GROUP_SEND_GRANT, GROUP_TEXT,
    GROUP_DELIVERY_ACK, GROUP_READ_RECEIPT, GROUP_ERROR
}
