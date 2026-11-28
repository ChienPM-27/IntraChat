package listener;

import model.ChatMessage;
import model.ChatSnapshot;
import model.DeliveryState;

public interface ChatClientListener {
    default void onFileTransfer(vn.intrachat.application.FileTransferService.Transfer transfer) { }
    void onDirectoryChanged(ChatSnapshot snapshot);

    void onMessageReceived(ChatMessage message);

    void onDeliveryStateChanged(String conversationId, String messageId, DeliveryState state);

    /** A recipient ACK count can change without a DeliveryState transition. */
    default void onGroupReceiptChanged(String conversationId,String messageId) { }

    default void onCallStateChanged(vn.intrachat.application.CallService.CallSession session) { }
    default void onLocalVideoFrame(java.awt.image.BufferedImage frame) { }
    default void onRemoteVideoFrame(java.awt.image.BufferedImage frame) { }

    void onConnectionError(String message);
}
