package com.milktea.agent.controller;

import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.milktea.agent.context.ContextManager;
import com.milktea.agent.memory.MemoryEntry;
import com.milktea.agent.memory.MemoryManager;
import com.milktea.agent.prompt.PromptManager;
import com.milktea.agent.rag.RagManager;
import com.milktea.agent.workflow.WorkflowDefinition;
import com.milktea.agent.workflow.WorkflowEngine;
import com.milktea.agent.workflow.WorkflowExecutionResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin endpoints for managing Memory, Skills, Context, Workflow, Prompts, and RAG.
 * Skills management now uses Spring AI Alibaba's SkillRegistry + SkillsAgentHook
 * with progressive disclosure support.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PromptManager promptManager;
    private final ContextManager contextManager;
    private final SkillRegistry skillRegistry;
    private final SkillsAgentHook skillsAgentHook;
    private final RagManager ragManager;
    private final MemoryManager memoryManager;
    private final WorkflowEngine workflowEngine;

    public AdminController(PromptManager promptManager,
                           ContextManager contextManager,
                           SkillRegistry skillRegistry,
                           SkillsAgentHook skillsAgentHook,
                           RagManager ragManager,
                           MemoryManager memoryManager,
                           WorkflowEngine workflowEngine) {
        this.promptManager = promptManager;
        this.contextManager = contextManager;
        this.skillRegistry = skillRegistry;
        this.skillsAgentHook = skillsAgentHook;
        this.ragManager = ragManager;
        this.memoryManager = memoryManager;
        this.workflowEngine = workflowEngine;
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
        return contextManager.getContextInfo(sessionId);
    }

    @DeleteMapping("/contexts/{sessionId}")
    public Map<String, String> clearSession(@PathVariable String sessionId) {
        contextManager.clearContext(sessionId);
        return Map.of("status", "ok", "message", "会话已清除: " + sessionId);
    }

    // ===== Skills Management (via Spring AI Alibaba SkillRegistry) =====

    @GetMapping("/skills")
    public Map<String, Object> getAllSkills() {
        return Map.of(
                "skills", skillsAgentHook.listSkills(),
                "totalCount", skillsAgentHook.getSkillCount()
        );
    }

    @GetMapping("/skills/{name}")
    public Map<String, Object> getSkill(@PathVariable String name) {
        boolean exists = skillsAgentHook.hasSkill(name);
        if (!exists) {
            return Map.of("error", "技能不存在: " + name);
        }
        String content = skillRegistry.readSkillContent(name);
        return Map.of(
                "name", name,
                "exists", true,
                "content", content != null ? content : ""
        );
    }

    @PostMapping("/skills/reload")
    public Map<String, String> reloadSkills() {
        skillRegistry.reload();
        return Map.of("status", "ok",
                "message", "技能已重新加载，当前技能数: " + skillsAgentHook.getSkillCount());
    }

    // ===== Memory Management =====

    @GetMapping("/memory/session/{sessionId}")
    public List<MemoryEntry> getSessionMemory(@PathVariable String sessionId) {
        return memoryManager.listSessionMemories(sessionId);
    }

    @PostMapping("/memory/session/{sessionId}")
    public Map<String, String> saveSessionMemory(@PathVariable String sessionId,
                                                  @RequestBody Map<String, String> request) {
        memoryManager.rememberSession(sessionId, request.get("key"),
                request.get("value"), request.getOrDefault("category", "manual"));
        return Map.of("status", "ok", "message", "会话记忆已保存");
    }

    @DeleteMapping("/memory/session/{sessionId}/{key}")
    public Map<String, String> deleteSessionMemory(@PathVariable String sessionId,
                                                     @PathVariable String key) {
        memoryManager.forgetSession(sessionId, key);
        return Map.of("status", "ok", "message", "会话记忆已删除");
    }

    @DeleteMapping("/memory/session/{sessionId}")
    public Map<String, String> clearSessionMemory(@PathVariable String sessionId) {
        memoryManager.clearSession(sessionId);
        return Map.of("status", "ok", "message", "会话记忆已清空");
    }

    @GetMapping("/memory/session/{sessionId}/search")
    public List<MemoryEntry> searchSessionMemory(@PathVariable String sessionId,
                                                  @RequestParam String keyword) {
        return memoryManager.searchSessionMemories(sessionId, keyword);
    }

    @GetMapping("/memory/user/{userId}")
    public List<MemoryEntry> getUserMemory(@PathVariable String userId) {
        return memoryManager.listUserMemories(userId);
    }

    @PostMapping("/memory/user/{userId}")
    public Map<String, String> saveUserMemory(@PathVariable String userId,
                                               @RequestBody Map<String, String> request) {
        memoryManager.rememberUser(userId, request.get("key"),
                request.get("value"), request.getOrDefault("category", "manual"));
        return Map.of("status", "ok", "message", "用户记忆已保存");
    }

    @DeleteMapping("/memory/user/{userId}/{key}")
    public Map<String, String> deleteUserMemory(@PathVariable String userId,
                                                  @PathVariable String key) {
        memoryManager.forgetUser(userId, key);
        return Map.of("status", "ok", "message", "用户记忆已删除");
    }

    @GetMapping("/memory/user/{userId}/search")
    public List<MemoryEntry> searchUserMemory(@PathVariable String userId,
                                               @RequestParam String keyword) {
        return memoryManager.searchUserMemories(userId, keyword);
    }

    @PostMapping("/memory/user/{userId}/preference")
    public Map<String, String> savePreference(@PathVariable String userId,
                                               @RequestBody Map<String, String> request) {
        memoryManager.savePreference(userId, request.get("key"), request.get("value"));
        return Map.of("status", "ok", "message", "用户偏好已保存");
    }

    @GetMapping("/memory/user/{userId}/preference/{prefKey}")
    public Map<String, String> getPreference(@PathVariable String userId,
                                              @PathVariable String prefKey) {
        String value = memoryManager.getPreference(userId, prefKey).orElse("");
        return Map.of("key", prefKey, "value", value);
    }

    // ===== Workflow Management =====

    @GetMapping("/workflows")
    public List<WorkflowDefinition> getAllWorkflows() {
        return workflowEngine.getAllWorkflows();
    }

    @GetMapping("/workflows/{workflowId}")
    public Map<String, Object> getWorkflow(@PathVariable String workflowId) {
        return workflowEngine.getWorkflow(workflowId)
                .<Map<String, Object>>map(w -> Map.of(
                        "id", w.id(),
                        "name", w.name(),
                        "description", w.description(),
                        "nodes", w.nodes(),
                        "edges", w.edges(),
                        "config", w.config() != null ? w.config() : Map.of()))
                .orElse(Map.of("error", "工作流不存在: " + workflowId));
    }

    @PostMapping("/workflows/{workflowId}/execute")
    public WorkflowExecutionResult executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, String> params) {
        return workflowEngine.execute(workflowId,
                params != null ? params : Map.of());
    }

    @PostMapping("/workflows/{workflowId}/execute-async")
    public Map<String, String> executeWorkflowAsync(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, String> params) {
        String executionId = workflowEngine.executeAsync(workflowId,
                params != null ? params : Map.of());
        return Map.of("executionId", executionId, "status", "started");
    }

    @PostMapping("/workflows/interrupt/{executionId}")
    public Map<String, Object> interruptWorkflow(@PathVariable String executionId) {
        boolean interrupted = workflowEngine.interrupt(executionId);
        return Map.of("executionId", executionId, "interrupted", interrupted);
    }

    @PostMapping("/workflows")
    public Map<String, String> registerWorkflow(@RequestBody WorkflowDefinition definition) {
        workflowEngine.registerWorkflow(definition);
        return Map.of("status", "ok", "message", "工作流已注册: " + definition.id());
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
