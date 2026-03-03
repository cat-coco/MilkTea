package com.milktea.agent.context;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Compresses context when it exceeds 1000 characters,
 * preserving core information and recent messages.
 */
@Component
public class ContextCompressor {

    private static final int MAX_CONTEXT_CHARS = 1000;
    private static final int KEEP_RECENT_MESSAGES = 6;

    /**
     * Compress the context if total character count exceeds threshold.
     * Keeps the most recent messages and summarizes older ones.
     */
    public String compress(AgentContext context) {
        List<AgentContext.ContextMessage> messages = context.getMessages();
        if (messages.isEmpty()) return "";

        int totalChars = context.getTotalCharCount();
        if (totalChars <= MAX_CONTEXT_CHARS) {
            return messages.stream()
                    .map(m -> m.role() + ": " + m.content())
                    .collect(Collectors.joining("\n"));
        }

        // Keep recent messages, summarize older ones
        int keepCount = Math.min(KEEP_RECENT_MESSAGES, messages.size());
        List<AgentContext.ContextMessage> olderMessages = messages.subList(0, messages.size() - keepCount);
        List<AgentContext.ContextMessage> recentMessages = messages.subList(messages.size() - keepCount, messages.size());

        StringBuilder compressed = new StringBuilder();

        // Summarize older messages
        if (!olderMessages.isEmpty()) {
            compressed.append("[历史摘要] ");
            for (AgentContext.ContextMessage msg : olderMessages) {
                String snippet = msg.content().length() > 50
                        ? msg.content().substring(0, 50) + "..."
                        : msg.content();
                compressed.append(msg.role()).append(":").append(snippet).append("; ");
            }
            compressed.append("\n---\n");
        }

        // Keep recent messages in full
        for (AgentContext.ContextMessage msg : recentMessages) {
            compressed.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }

        return compressed.toString();
    }

    /**
     * Build a context string suitable for LLM input.
     */
    public String buildContextForLLM(AgentContext context) {
        StringBuilder sb = new StringBuilder();

        // Add skill execution status if any
        List<AgentContext.SkillExecution> executions = context.getSkillExecutions();
        if (!executions.isEmpty()) {
            sb.append("[技能执行记录]\n");
            for (AgentContext.SkillExecution exec : executions) {
                sb.append("- ").append(exec.skillName())
                        .append(": ").append(exec.status())
                        .append("\n");
            }
            sb.append("---\n");
        }

        // Add compressed conversation
        sb.append(compress(context));

        return sb.toString();
    }
}
