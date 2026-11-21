package listener;

import model.CallSession;
import model.ChatMessage;
import model.ChatSnapshot;
import model.DeliveryState;

public interface ChatClientListener {
    void onDirectoryChanged(ChatSnapshot snapshot);

    void onMessageReceived(ChatMessage message);

    void onDeliveryStateChanged(String conversationId, String messageId, DeliveryState state);

    default void onCallChanged(CallSession session) {}

    void onConnectionError(String message);
}
