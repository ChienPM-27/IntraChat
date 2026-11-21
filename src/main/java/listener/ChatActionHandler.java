package listener;

import model.CallSession;
import model.ChatMessage;
import model.ChatUser;
import model.Conversation;
import model.MessageContent;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ChatActionHandler extends AutoCloseable {
    CompletionStage<ChatUser> signIn(String username, char[] password);

    CompletionStage<ChatUser> register(String username, char[] password);

    void connect(ChatClientListener listener);

    CompletionStage<List<ChatMessage>> loadHistory(String conversationId);

    CompletionStage<ChatMessage> sendMessage(String conversationId, MessageContent content);

    CompletionStage<Conversation> createGroup(String name, List<String> memberIds);

    CompletionStage<Void> sendFriendRequest(String ipAddress);

    CompletionStage<Void> respondToFriendRequest(String requestId, boolean accept);

    CompletionStage<Void> startCall(String conversationId, CallSession.Mode mode);

    CompletionStage<Void> respondToCall(String callId, boolean accept);

    CompletionStage<Void> endCall(String callId);

    @Override
    void close();
}
