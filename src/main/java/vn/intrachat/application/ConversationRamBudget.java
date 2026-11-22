package vn.intrachat.application;

import java.io.IOException;

/**
 * Session-scoped bounded in-memory conversation accounting. This budget is
 * erased on session disconnect and is NEVER persisted as History.
 */
final class ConversationRamBudget {
    private final int maxMessages;
    private final int maxBytes;
    private int count;
    private int bytes;

    ConversationRamBudget(int maxMessages,int maxBytes) {
        if(maxMessages<1||maxBytes<1)
            throw new IllegalArgumentException("Positive RAM conversation bounds required");
        this.maxMessages=maxMessages;
        this.maxBytes=maxBytes;
    }

    synchronized void reserve(int messageBytes)throws IOException{
        if(messageBytes<0)throw new IllegalArgumentException("Negative message size");
        if(count>=maxMessages||messageBytes>maxBytes-bytes)
            throw new IOException("CHAT_CAPACITY_EXCEEDED");
        count++;
        bytes+=messageBytes;
    }
    synchronized int count(){return count;}
    synchronized int bytes(){return bytes;}
    synchronized void clear(){count=0;bytes=0;}
}
