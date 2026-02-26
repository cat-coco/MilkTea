package com.milktea.agent.context;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationContextManager {

    private static final int MAX_HISTORY_SIZE = 50;

    private final Map<String, List<Message>> conversationHistories = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> sessionAttributes = new ConcurrentHashMap<>();

    public List<Message> getHistory(String sessionId) {
        return conversationHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public void addUserMessage(String sessionId, String content) {
        List<Message> history = getHistory(sessionId);
        history.add(new UserMessage(content));
        trimHistory(history);
    }

    public void addAssistantMessage(String sessionId, String content) {
        List<Message> history = getHistory(sessionId);
        history.add(new AssistantMessage(content));
        trimHistory(history);
    }

    public void addSystemMessage(String sessionId, String content) {
        List<Message> history = getHistory(sessionId);
        history.add(new SystemMessage(content));
    }

    public void clearHistory(String sessionId) {
        conversationHistories.remove(sessionId);
        sessionAttributes.remove(sessionId);
    }

    public void setAttribute(String sessionId, String key, Object value) {
        sessionAttributes.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(key, value);
    }

    public Object getAttribute(String sessionId, String key) {
        Map<String, Object> attrs = sessionAttributes.get(sessionId);
        return attrs != null ? attrs.get(key) : null;
    }

    public Map<String, Object> getAllAttributes(String sessionId) {
        return sessionAttributes.getOrDefault(sessionId, Map.of());
    }

    public Set<String> getActiveSessions() {
        return conversationHistories.keySet();
    }

    public int getHistorySize(String sessionId) {
        List<Message> history = conversationHistories.get(sessionId);
        return history != null ? history.size() : 0;
    }

    private void trimHistory(List<Message> history) {
        while (history.size() > MAX_HISTORY_SIZE) {
            // Keep system messages, remove oldest non-system messages
            if (history.get(0) instanceof SystemMessage) {
                if (history.size() > 1) {
                    history.remove(1);
                } else {
                    break;
                }
            } else {
                history.remove(0);
            }
        }
    }
}
