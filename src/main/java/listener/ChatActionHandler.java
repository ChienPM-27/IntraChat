package listener;

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

    @Override
    void close();
}
