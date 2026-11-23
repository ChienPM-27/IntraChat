package listener;

import model.ChatMessage;
import model.Conversation;
import model.MessageContent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ChatActionHandler extends AutoCloseable {
    void connect(ChatClientListener listener);

    CompletionStage<List<ChatMessage>> loadHistory(String conversationId);

    CompletionStage<ChatMessage> sendMessage(String conversationId, MessageContent content);

    /** Only an explicitly clicked manual resend for an UNCERTAIN Live attempt. */
    default CompletionStage<ChatMessage> retryMessage(String conversationId,String messageId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
            "This chat mode does not support manual uncertain-message retry"));
    }

    default void acceptFile(String transferId,java.nio.file.Path destination) throws java.io.IOException {
        throw new UnsupportedOperationException("File reception unavailable");
    }
    default void rejectFile(String transferId) throws java.io.IOException {
        throw new UnsupportedOperationException("File rejection unavailable");
    }
    default void cancelFile(String transferId) { }
    CompletionStage<Conversation> createGroup(String name, List<String> memberIds);
    default boolean groupAvailable(){return false;}
    default CompletionStage<Void> leaveGroup(String id){return CompletableFuture.failedFuture(new UnsupportedOperationException("Leave unavailable"));}
    default CompletionStage<Void> removeGroupMember(String id,String member){return CompletableFuture.failedFuture(new UnsupportedOperationException("Remove unavailable"));}
    default boolean isGroupOwner(String id){return false;}
    default boolean groupOwnerOnline(String id){return true;}
    /** Evidence for each recipient of a group message; null for direct messages. */
    default String groupReceiptLabel(String conversationId,String messageId){return null;}

    /** Manage the existing live LAN session from the Messenger interface. */
    default boolean connectionSettingsAvailable() { return false; }
    default void openConnectionSettings() { }
    /** Called only after foreground selected conversation bubble is actually visible. */
    default void onMessageVisible(String conversationId, String messageId) { }

    @Override
    void close();
}