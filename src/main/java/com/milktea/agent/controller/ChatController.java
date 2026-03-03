package com.milktea.agent.controller;

import com.milktea.agent.context.ConversationContextManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import com.milktea.agent.react.AgentResult;
import com.milktea.agent.react.AgentStep;
import com.milktea.agent.react.ReactAgent;
import com.milktea.agent.react.ToolExecutor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ReactAgent reactAgent;
    private final PromptManager promptManager;
    private final ConversationContextManager contextManager;
    private final RagManager ragManager;
    private final ToolExecutor toolExecutor;

    public ChatController(ReactAgent reactAgent,
                          PromptManager promptManager,
                          ConversationContextManager contextManager,
                          RagManager ragManager,
                          ToolExecutor toolExecutor) {
        this.reactAgent = reactAgent;
        this.promptManager = promptManager;
        this.contextManager = contextManager;
        this.ragManager = ragManager;
        this.toolExecutor = toolExecutor;
    }

    @PostMapping("/send")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        String userMessage = request.message();

        // RAG: retrieve relevant knowledge
        String ragContext = ragManager.getRelevantContext(userMessage);

        // Build system prompt with ReAct instructions, tool descriptions, and RAG context
        String systemPrompt = promptManager.getPrompt("system");
        systemPrompt += promptManager.getPrompt("react_instructions");
        systemPrompt += "\n\n【可用工具】\n" + toolExecutor.getToolDescriptions();
        if (!ragContext.isBlank()) {
            systemPrompt += "\n\n【参考知识库信息】\n" + ragContext;
        }

        // Add user message to context
        contextManager.addUserMessage(sessionId, userMessage);

        // Get conversation history
        List<Message> history = new ArrayList<>(contextManager.getHistory(sessionId));

        // Execute ReAct agent
        AgentResult result = reactAgent.execute(systemPrompt, history);

        // Save assistant response to context
        contextManager.addAssistantMessage(sessionId, result.finalAnswer());

        // Build step data for frontend
        List<Map<String, Object>> stepData = new ArrayList<>();
        for (AgentStep step : result.steps()) {
            stepData.add(Map.of(
                    "type", step.type().name(),
                    "label", step.type().getLabel(),
                    "content", step.content(),
                    "timestamp", step.timestamp()
            ));
        }

        return Map.of(
                "sessionId", sessionId,
                "reply", result.finalAnswer(),
                "historySize", contextManager.getHistorySize(sessionId),
                "agentSteps", stepData,
                "iterations", result.iterations()
        );
    }

    @PostMapping("/clear")
    public Map<String, String> clearContext(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId != null) {
            contextManager.clearHistory(sessionId);
        }
        return Map.of("status", "ok", "message", "对话已清空");
    }

    @GetMapping("/welcome")
    public Map<String, String> welcome() {
        return Map.of("message", promptManager.getPrompt("welcome"));
    }

    public record ChatRequest(String sessionId, String message) {}
}
