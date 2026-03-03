package com.milktea.agent.context;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-session contexts with browser tab isolation.
 * Each tab has its own isolated context identified by tabId.
 */
@Component
public class ContextManager {

    private static final int MAX_HISTORY_SIZE = 50;

    // tabId -> AgentContext
    private final Map<String, AgentContext> contexts = new ConcurrentHashMap<>();
    // sessionId -> List<Message> (for Spring AI compatibility)
    private final Map<String, List<Message>> messageHistories = new ConcurrentHashMap<>();

    public AgentContext getOrCreateContext(String sessionId, String tabId) {
        String key = tabId != null ? tabId : sessionId;
        return contexts.computeIfAbsent(key, k -> new AgentContext(sessionId, tabId));
    }

    public AgentContext getContext(String sessionId) {
        return contexts.get(sessionId);
    }

    public void addUserMessage(String sessionId, String content) {
        AgentContext ctx = contexts.get(sessionId);
        if (ctx != null) ctx.addUserMessage(content);

        List<Message> history = messageHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new UserMessage(content));
        trimHistory(history);
    }

    public void addAssistantMessage(String sessionId, String content) {
        AgentContext ctx = contexts.get(sessionId);
        if (ctx != null) ctx.addAgentReply(content);

        List<Message> history = messageHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new AssistantMessage(content));
        trimHistory(history);
    }

    public void addSkillExecution(String sessionId, String skillId, String skillName,
                                   String status, String result) {
        AgentContext ctx = contexts.get(sessionId);
        if (ctx != null) ctx.addSkillExecution(skillId, skillName, status, result);
    }

    public List<Message> getHistory(String sessionId) {
        return messageHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public int getHistorySize(String sessionId) {
        List<Message> history = messageHistories.get(sessionId);
        return history != null ? history.size() : 0;
    }

    public void clearContext(String sessionId) {
        contexts.remove(sessionId);
        messageHistories.remove(sessionId);
    }

    public Set<String> getActiveSessions() {
        return Set.copyOf(contexts.keySet());
    }

    public Map<String, Object> getContextInfo(String sessionId) {
        AgentContext ctx = contexts.get(sessionId);
        if (ctx == null) return Map.of();
        return Map.of(
                "sessionId", ctx.getSessionId(),
                "tabId", ctx.getTabId() != null ? ctx.getTabId() : "",
                "createdAt", ctx.getCreatedAt().toString(),
                "messageCount", ctx.getMessages().size(),
                "totalChars", ctx.getTotalCharCount(),
                "skillExecutions", ctx.getSkillExecutions().size(),
                "metadata", ctx.getAllMetadata()
        );
    }

    private void trimHistory(List<Message> history) {
        while (history.size() > MAX_HISTORY_SIZE) {
            history.removeFirst();
        }
    }
}
