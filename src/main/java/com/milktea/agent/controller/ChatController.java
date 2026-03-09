package com.milktea.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.milktea.agent.context.ContextManager;
import com.milktea.agent.memory.MemoryEntry;
import com.milktea.agent.memory.MemoryManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat endpoint using spring-ai-alibaba ReactAgent with SkillsAgentHook
 * for progressive disclosure of skills, integrated with Memory, Context, and RAG modules.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ReactAgent mainAgent;
    private final ChatModel chatModel;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final SkillsAgentHook skillsAgentHook;
    private final PromptManager promptManager;
    private final RagManager ragManager;

    public ChatController(@Qualifier("mainAgent") ReactAgent mainAgent,
                          ChatModel chatModel,
                          ContextManager contextManager,
                          MemoryManager memoryManager,
                          SkillsAgentHook skillsAgentHook,
                          PromptManager promptManager,
                          RagManager ragManager) {
        this.mainAgent = mainAgent;
        this.chatModel = chatModel;
        this.contextManager = contextManager;
        this.memoryManager = memoryManager;
        this.skillsAgentHook = skillsAgentHook;
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

        // 2. RAG knowledge retrieval
        String ragContext = ragManager.getRelevantContext(userMessage);

        // 3. Session memory context
        List<MemoryEntry> memories = memoryManager.searchSessionMemories(sessionId, userMessage);

        // 4. Execute via ReactAgent with SkillsAgentHook (progressive disclosure)
        //    The SkillsAgentHook automatically:
        //    - Injects skill list (name + description only) into system prompt
        //    - Provides read_skill tool for on-demand full SKILL.md loading
        //    - Activates grouped tools only after the related skill is loaded
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

        // 5. Save context and memory
        contextManager.addAssistantMessage(contextKey, reply);
        memoryManager.rememberSession(sessionId, "last_input", userMessage, "chat");
        memoryManager.rememberSession(sessionId, "last_reply", reply, "chat");

        return Map.of(
                "sessionId", sessionId,
                "reply", reply,
                "historySize", contextManager.getHistorySize(contextKey),
                "availableSkills", skillsAgentHook.listSkills(),
                "skillCount", skillsAgentHook.getSkillCount(),
                "agentSteps", List.of(),
                "iterations", 0
        );
    }

    /**
     * Streaming chat endpoint using Server-Sent Events.
     * Uses mainAgent.call() first (preserving skill capabilities like order/query/cancel).
     * Falls back to chatModel.stream() for true token-by-token streaming when agent fails.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestBody ChatRequest request) {
        String sid = request.sessionId();
        if (sid == null || sid.isBlank()) {
            sid = UUID.randomUUID().toString();
        }
        final String sessionId = sid;
        String tabId = request.tabId();
        String contextKey = tabId != null && !tabId.isBlank() ? tabId : sessionId;
        String userMessage = request.message();

        // Initialize context
        contextManager.getOrCreateContext(sessionId, tabId);
        contextManager.addUserMessage(contextKey, userMessage);

        // Build enriched prompt for fallback path
        String ragContext = ragManager.getRelevantContext(userMessage);
        List<MemoryEntry> memories = memoryManager.searchSessionMemories(sessionId, userMessage);

        StringBuilder fullReply = new StringBuilder();

        return Flux.concat(
                // 1. Send session metadata
                Flux.just(ServerSentEvent.<String>builder()
                        .event("meta")
                        .data("{\"sessionId\":\"" + sessionId + "\"}")
                        .build()),

                // 2. Try mainAgent first (with skills), fall back to chatModel.stream()
                Mono.fromCallable(() -> mainAgent.call(userMessage))
                        .subscribeOn(Schedulers.boundedElastic())
                        .filter(reply -> reply != null && !reply.isBlank())
                        .flux()
                        .doOnNext(fullReply::append)
                        .map(reply -> ServerSentEvent.<String>builder()
                                .event("token")
                                .data(reply)
                                .build())
                        .onErrorResume(e -> {
                            // Agent failed, fall back to chatModel streaming
                            List<Message> messages = buildMessages(userMessage, ragContext, memories);
                            return chatModel.stream(new Prompt(messages))
                                    .mapNotNull(resp -> resp.getResult() != null
                                            ? resp.getResult().getOutput().getText() : null)
                                    .filter(content -> !content.isEmpty())
                                    .transform(this::filterThinkingTokens)
                                    .doOnNext(fullReply::append)
                                    .map(content -> ServerSentEvent.<String>builder()
                                            .event("token")
                                            .data(content)
                                            .build());
                        }),

                // 3. Save context & send done
                Flux.defer(() -> {
                    String reply = fullReply.toString();
                    if (reply.isBlank()) reply = "我没有理解您的意思，请再说一次~";
                    contextManager.addAssistantMessage(contextKey, reply);
                    memoryManager.rememberSession(sessionId, "last_input", userMessage, "chat");
                    memoryManager.rememberSession(sessionId, "last_reply", reply, "chat");
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("done")
                            .data("[DONE]")
                            .build());
                })
        );
    }

    /**
     * Build enriched message list with system prompt, RAG context, and memory.
     */
    private List<Message> buildMessages(String userMessage, String ragContext,
                                         List<MemoryEntry> memories) {
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
        return messages;
    }

    /**
     * Filter out model's &lt;think&gt;...&lt;/think&gt; reasoning tokens from the stream.
     * Some models (e.g. qwen) emit thinking/reasoning in &lt;think&gt; blocks before the actual response.
     * This method buffers until thinking is complete, then streams the real content.
     */
    private Flux<String> filterThinkingTokens(Flux<String> tokens) {
        StringBuilder buffer = new StringBuilder();
        AtomicBoolean passThrough = new AtomicBoolean(false);

        return tokens.concatMap(token -> {
            // After thinking is done, stream tokens directly
            if (passThrough.get()) {
                return Flux.just(token);
            }

            buffer.append(token);
            String buf = buffer.toString();

            // Check if </think> end tag is found
            int endIdx = buf.indexOf("</think>");
            if (endIdx >= 0) {
                passThrough.set(true);
                buffer.setLength(0);
                String afterThink = buf.substring(endIdx + "</think>".length()).strip();
                return afterThink.isEmpty() ? Flux.empty() : Flux.just(afterThink);
            }

            // If buffer doesn't start with '<' or is long enough without <think>, no thinking mode
            if (!buf.startsWith("<") || (buf.length() >= 7 && !buf.startsWith("<think"))) {
                passThrough.set(true);
                buffer.setLength(0);
                return Flux.just(buf);
            }

            // Still buffering, waiting to detect thinking mode
            return Flux.empty();
        });
    }

    /**
     * Fallback when ReactAgent invocation fails: use ChatModel directly.
     */
    private String fallbackChat(String userMessage, String ragContext,
                                 List<MemoryEntry> memories) {
        try {
            List<Message> messages = buildMessages(userMessage, ragContext, memories);
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
