package com.milktea.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.milktea.agent.context.ContextManager;
import com.milktea.agent.memory.MemoryEntry;
import com.milktea.agent.memory.MemoryManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import com.milktea.agent.skill.SkillDefinition;
import com.milktea.agent.skill.SkillManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Chat endpoint using spring-ai-alibaba ReactAgent for ReAct-style tool-calling,
 * integrated with Memory, Context, Skills, and RAG modules.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ReactAgent mainAgent;
    private final ChatModel chatModel;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final SkillManager skillManager;
    private final PromptManager promptManager;
    private final RagManager ragManager;

    public ChatController(@Qualifier("mainAgent") ReactAgent mainAgent,
                          ChatModel chatModel,
                          ContextManager contextManager,
                          MemoryManager memoryManager,
                          SkillManager skillManager,
                          PromptManager promptManager,
                          RagManager ragManager) {
        this.mainAgent = mainAgent;
        this.chatModel = chatModel;
        this.contextManager = contextManager;
        this.memoryManager = memoryManager;
        this.skillManager = skillManager;
        this.promptManager = promptManager;
        this.ragManager = ragManager;
    }

    @PostMapping("/send")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String tabId = request.tabId();
        String contextKey = tabId != null && !tabId.isBlank() ? tabId : sessionId;
        String userMessage = request.message();

        // 1. Initialize context with tab isolation
        contextManager.getOrCreateContext(sessionId, tabId);
        contextManager.addUserMessage(contextKey, userMessage);

        // 2. Check keyword-triggered skills
        List<SkillDefinition> triggered = skillManager.findTriggeredSkills(userMessage);
        List<String> triggeredSkillNames = triggered.stream()
                .map(SkillDefinition::name).toList();

        // 3. RAG knowledge retrieval
        String ragContext = ragManager.getRelevantContext(userMessage);

        // 4. Session memory context
        List<MemoryEntry> memories = memoryManager.searchSessionMemories(sessionId, userMessage);

        // 5. Execute via ReactAgent (spring-ai-alibaba-agent-framework)
        String reply;
        try {
            reply = mainAgent.call(userMessage);
        } catch (Exception e) {
            // Fallback: direct ChatModel invocation
            reply = fallbackChat(userMessage, ragContext, memories);
        }

        if (reply == null || reply.isBlank()) {
            reply = "我没有理解您的意思，请再说一次~";
        }

        // 6. Save context and memory
        contextManager.addAssistantMessage(contextKey, reply);
        memoryManager.rememberSession(sessionId, "last_input", userMessage, "chat");
        memoryManager.rememberSession(sessionId, "last_reply", reply, "chat");

        // 7. Track skill executions
        for (SkillDefinition skill : triggered) {
            contextManager.addSkillExecution(contextKey, skill.id(), skill.name(),
                    "triggered", "");
        }

        return Map.of(
                "sessionId", sessionId,
                "reply", reply,
                "historySize", contextManager.getHistorySize(contextKey),
                "triggeredSkills", triggeredSkillNames,
                "agentSteps", List.of(),
                "iterations", 0
        );
    }

    /**
     * Fallback when ReactAgent invocation fails: use ChatModel directly.
     */
    private String fallbackChat(String userMessage, String ragContext,
                                 List<MemoryEntry> memories) {
        try {
            StringBuilder systemPrompt = new StringBuilder(promptManager.getPrompt("system"));
            if (ragContext != null && !ragContext.isBlank()) {
                systemPrompt.append("\n\n【参考知识库信息】\n").append(ragContext);
            }
            if (!memories.isEmpty()) {
                systemPrompt.append("\n\n【对话记忆】\n");
                memories.forEach(m -> systemPrompt.append("- ")
                        .append(m.key()).append(": ").append(m.value()).append("\n"));
            }
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt.toString()));
            messages.add(new UserMessage(userMessage));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            return "抱歉，系统暂时无法处理您的请求，请稍后重试~";
        }
    }

    @PostMapping("/clear")
    public Map<String, String> clearContext(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId != null) {
            contextManager.clearContext(sessionId);
            memoryManager.clearSession(sessionId);
        }
        return Map.of("status", "ok", "message", "对话已清空");
    }

    @GetMapping("/welcome")
    public Map<String, String> welcome() {
        return Map.of("message", promptManager.getPrompt("welcome"));
    }

    public record ChatRequest(String sessionId, String tabId, String message) {}
}
