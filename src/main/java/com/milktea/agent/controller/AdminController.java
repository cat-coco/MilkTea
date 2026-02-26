package com.milktea.agent.controller;

import com.milktea.agent.context.ConversationContextManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import com.milktea.agent.skill.SkillRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PromptManager promptManager;
    private final ConversationContextManager contextManager;
    private final SkillRegistry skillRegistry;
    private final RagManager ragManager;

    public AdminController(PromptManager promptManager,
                           ConversationContextManager contextManager,
                           SkillRegistry skillRegistry,
                           RagManager ragManager) {
        this.promptManager = promptManager;
        this.contextManager = contextManager;
        this.skillRegistry = skillRegistry;
        this.ragManager = ragManager;
    }

    // ===== Prompt Management =====

    @GetMapping("/prompts")
    public Map<String, String> getAllPrompts() {
        return promptManager.getAllPrompts();
    }

    @PostMapping("/prompts")
    public Map<String, String> updatePrompt(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        String template = request.get("template");
        promptManager.setPrompt(key, template);
        return Map.of("status", "ok", "message", "提示词已更新: " + key);
    }

    @DeleteMapping("/prompts/{key}")
    public Map<String, String> deletePrompt(@PathVariable String key) {
        promptManager.removePrompt(key);
        return Map.of("status", "ok", "message", "提示词已删除: " + key);
    }

    // ===== Context Management =====

    @GetMapping("/contexts")
    public Map<String, Object> getContextInfo() {
        Set<String> sessions = contextManager.getActiveSessions();
        return Map.of(
                "activeSessions", sessions.size(),
                "sessionIds", sessions
        );
    }

    @GetMapping("/contexts/{sessionId}")
    public Map<String, Object> getSessionInfo(@PathVariable String sessionId) {
        return Map.of(
                "sessionId", sessionId,
                "historySize", contextManager.getHistorySize(sessionId),
                "attributes", contextManager.getAllAttributes(sessionId)
        );
    }

    @DeleteMapping("/contexts/{sessionId}")
    public Map<String, String> clearSession(@PathVariable String sessionId) {
        contextManager.clearHistory(sessionId);
        return Map.of("status", "ok", "message", "会话已清除: " + sessionId);
    }

    // ===== Skills Management =====

    @GetMapping("/skills")
    public List<SkillRegistry.SkillInfo> getAllSkills() {
        return skillRegistry.getAllSkills();
    }

    @PostMapping("/skills/{name}/toggle")
    public Map<String, String> toggleSkill(@PathVariable String name, @RequestBody Map<String, Boolean> request) {
        boolean enabled = request.getOrDefault("enabled", true);
        skillRegistry.setEnabled(name, enabled);
        return Map.of("status", "ok", "message", "技能 " + name + " 已" + (enabled ? "启用" : "禁用"));
    }

    // ===== RAG Management =====

    @GetMapping("/rag")
    public List<RagManager.KnowledgeEntry> getAllKnowledge() {
        return ragManager.getAllKnowledge();
    }

    @PostMapping("/rag")
    public Map<String, String> addKnowledge(@RequestBody Map<String, Object> request) {
        String id = (String) request.get("id");
        String title = (String) request.get("title");
        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) request.get("keywords");
        String content = (String) request.get("content");
        ragManager.addKnowledge(id, title, keywords, content);
        return Map.of("status", "ok", "message", "知识条目已添加: " + title);
    }

    @DeleteMapping("/rag/{id}")
    public Map<String, String> removeKnowledge(@PathVariable String id) {
        ragManager.removeKnowledge(id);
        return Map.of("status", "ok", "message", "知识条目已删除: " + id);
    }

    @GetMapping("/rag/search")
    public List<RagManager.KnowledgeEntry> searchKnowledge(@RequestParam String query) {
        return ragManager.search(query);
    }
}
