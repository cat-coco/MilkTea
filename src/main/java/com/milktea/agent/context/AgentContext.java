package com.milktea.agent.context;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Rich context containing user input, agent replies, skill execution status, and session metadata.
 */
public class AgentContext {

    private final String sessionId;
    private final String tabId;
    private final LocalDateTime createdAt;
    private final List<ContextMessage> messages = new ArrayList<>();
    private final Map<String, Object> metadata = new LinkedHashMap<>();
    private final List<SkillExecution> skillExecutions = new ArrayList<>();

    public AgentContext(String sessionId, String tabId) {
        this.sessionId = sessionId;
        this.tabId = tabId;
        this.createdAt = LocalDateTime.now();
    }

    public void addUserMessage(String content) {
        messages.add(new ContextMessage("user", content, LocalDateTime.now()));
    }

    public void addAgentReply(String content) {
        messages.add(new ContextMessage("agent", content, LocalDateTime.now()));
    }

    public void addSkillExecution(String skillId, String skillName, String status, String result) {
        skillExecutions.add(new SkillExecution(skillId, skillName, status, result, LocalDateTime.now()));
    }

    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public String getSessionId() { return sessionId; }
    public String getTabId() { return tabId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ContextMessage> getMessages() { return List.copyOf(messages); }
    public Map<String, Object> getAllMetadata() { return Map.copyOf(metadata); }
    public List<SkillExecution> getSkillExecutions() { return List.copyOf(skillExecutions); }

    public int getTotalCharCount() {
        return messages.stream().mapToInt(m -> m.content().length()).sum();
    }

    public record ContextMessage(String role, String content, LocalDateTime timestamp) {}
    public record SkillExecution(String skillId, String skillName, String status,
                                  String result, LocalDateTime executedAt) {}
}
