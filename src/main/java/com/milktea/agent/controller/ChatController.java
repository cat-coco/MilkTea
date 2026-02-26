package com.milktea.agent.controller;

import com.milktea.agent.context.ConversationContextManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final PromptManager promptManager;
    private final ConversationContextManager contextManager;
    private final RagManager ragManager;

    public ChatController(ChatModel chatModel,
                          PromptManager promptManager,
                          ConversationContextManager contextManager,
                          RagManager ragManager) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultFunctions("createOrder", "cancelOrder", "queryOrder")
                .build();
        this.promptManager = promptManager;
        this.contextManager = contextManager;
        this.ragManager = ragManager;
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

        // Build system prompt with RAG context
        String systemPrompt = promptManager.getPrompt("system");
        if (!ragContext.isBlank()) {
            systemPrompt += "\n\n【参考知识库信息】\n" + ragContext;
        }

        // Add user message to context
        contextManager.addUserMessage(sessionId, userMessage);

        // Build message list with history
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(contextManager.getHistory(sessionId));

        // Call AI
        String response = chatClient.prompt(new Prompt(messages))
                .call()
                .content();

        // Save assistant response to context
        contextManager.addAssistantMessage(sessionId, response);

        return Map.of(
                "sessionId", sessionId,
                "reply", response,
                "historySize", contextManager.getHistorySize(sessionId)
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
