package vn.intrachat.domain;

/** Connection state of a device, NOT the delivery or read state of a message. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED;

    public static ConnectionState initial() {
        return DISCONNECTED;
    }
}
