package vn.intrachat.application;

import java.util.UUID;

/** Application-visible connection events; none represents a read receipt or trust assertion. */
public final class NetworkEvents {
    private NetworkEvents() {}
    public enum Kind { LISTENING, LISTENER_STOPPED, CONNECTING, WAITING_REMOTE_APPROVAL, PENDING_APPROVAL,
        CONNECTED, DISCONNECTED, FAILED, DIAGNOSTIC }
    public record Event(Kind kind, String text, UUID remoteDeviceId) { }
}
