package com.milktea.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.milktea.agent.context.ContextManager;
import com.milktea.agent.memory.MemoryEntry;
import com.milktea.agent.memory.MemoryManager;
import com.milktea.agent.service.MockDataService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import com.milktea.agent.service.WorkflowPlanService;
import com.milktea.agent.service.WorkflowPlanService.WorkflowStep;
import com.milktea.agent.service.WorkflowPlanService.SubTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 财报分析聊天控制器 - 处理 frontend-extensionV2 的所有前端请求。
 * Sheet 数据来源于 resources/mockdata/finance-mock-data.xlsx，可通过修改 Excel 实现数据可配置化。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceChatController {

    private final ReactAgent financeMainAgent;
    private final ChatModel chatModel;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final SkillsAgentHook financeSkillsAgentHook;
    private final MockDataService mockDataService;
    private final WorkflowPlanService workflowPlanService;

    private final Map<String, WorkflowState> workflowStates = new ConcurrentHashMap<>();

    /** 每个子任务的流式进度提示 */
    private static final Map<String, List<String>> STEP_PROGRESS = Map.ofEntries(
            Map.entry("step1", List.of("正在连接EFM报表系统...", "正在读取报表数据...",
                    "正在计算各报表项波动比例...", "正在标注异常波动项（波动>20%）...")),
            Map.entry("step2", List.of("正在查询DCF010102明细数据...",
                    "正在提取波动超20%报表项的明细...", "正在写入明细数据到Sheet...")),
            Map.entry("step3_1", List.of("正在查询股权关系信息...", "正在标注被持股公司8大分层...")),
            Map.entry("step3_2", List.of("正在关联明细数据与股权数据...", "正在标注公司层级和IC层级...")),
            Map.entry("step3_3", List.of("正在读取SR6数据...", "正在更新IC层级信息...",
                    "正在追加公司层级、IC层级列...")),
            Map.entry("step3_4", List.of("正在按公司层级过滤数据...", "正在按IC层级及JC过滤...",
                    "正在创建处理后数据...")),
            Map.entry("step4", List.of("正在汇总明细数据金额...", "正在读取报表期间金额...",
                    "正在进行数据一致性比对校验...")),
            Map.entry("step5", List.of("正在按简化场景汇聚数据...", "正在分析各场景波动原因...",
                    "正在生成波动分析结论...", "正在写入结论到Sheet..."))
    );

    public FinanceChatController(@Qualifier("financeMainAgent") ReactAgent financeMainAgent,
                                  ChatModel chatModel,
                                  ContextManager contextManager,
                                  MemoryManager memoryManager,
                                  @Qualifier("financeSkillsAgentHook") SkillsAgentHook financeSkillsAgentHook,
                                  MockDataService mockDataService,
                                  WorkflowPlanService workflowPlanService) {
        this.financeMainAgent = financeMainAgent;
        this.chatModel = chatModel;
        this.contextManager = contextManager;
        this.memoryManager = memoryManager;
        this.financeSkillsAgentHook = financeSkillsAgentHook;
        this.mockDataService = mockDataService;
        this.workflowPlanService = workflowPlanService;
    }

    @PostMapping("/send")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }
        String userMessage = request.message();

        contextManager.getOrCreateContext(sessionId, null);
        contextManager.addUserMessage(sessionId, userMessage);

        List<MemoryEntry> memories = memoryManager.searchSessionMemories(sessionId, userMessage);

        boolean isWorkflowTrigger = isAnalysisTrigger(userMessage);
        boolean isEntityResponse = isEntityResponse(sessionId, userMessage);

        String reply;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        if (isWorkflowTrigger) {
            WorkflowState state = new WorkflowState();
            state.phase = "awaiting_entity";
            workflowStates.put(sessionId, state);

            reply = "请告知我您需要对哪一个实体公司进行现金流报表波动合理性分析？（期间默认当前期间）";
            result.put("reply", reply);
            result.put("workflowActive", false);

        } else if (isEntityResponse) {
            WorkflowState state = workflowStates.get(sessionId);
            state.entity = userMessage.trim();
            state.phase = "running";
            state.currentStep = 0;

            reply = String.format("我来帮你完成%s的现金流报表的波动分析。"
                    + "我会提取报表数据、计算波动比例、标注异常波动（波动比例大于20%%），"
                    + "并对明细数据进行提取及分析，最后输出报表分析结果。", state.entity);

            result.put("reply", reply);
            result.put("workflowActive", true);

        } else {
            try {
                reply = financeMainAgent.call(userMessage).getText();
            } catch (Exception e) {
                reply = fallbackChat(userMessage, memories);
            }
            if (reply == null || reply.isBlank()) {
                reply = "抱歉，我没有理解您的请求，能否请您再说一次？";
            }
            result.put("reply", reply);
            result.put("workflowActive", false);
        }

        contextManager.addAssistantMessage(sessionId, reply);
        memoryManager.rememberSession(sessionId, "last_input", userMessage, "finance_chat");
        memoryManager.rememberSession(sessionId, "last_reply", reply, "finance_chat");

        return result;
    }

    @PostMapping("/execute-step")
    public Map<String, Object> executeStep(@RequestBody StepRequest request) {
        String sessionId = request.sessionId();
        String stepId = request.stepId();
        WorkflowState state = workflowStates.get(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        if (state == null) {
            result.put("stepReply", "未找到活跃的工作流，请重新发起分析。");
            return result;
        }

        switch (stepId) {
            case "step1" -> executeStep1(state, result);
            case "step2" -> executeStep2(state, result);
            case "step3_1" -> executeStep3_1(state, result);
            case "step3_2" -> executeStep3_2(state, result);
            case "step3_3" -> executeStep3_3(state, result);
            case "step3_4" -> executeStep3_4(state, result);
            case "step4" -> executeStep4(state, result);
            case "step5" -> executeStep5(state, result);
            default -> result.put("stepReply", "未知步骤：" + stepId);
        }

        return result;
    }

    /**
     * 流式执行步骤 —— 先逐条推送 progress JSON，再推送 excel/complete JSON。
     * 复用已有 executeStepX 方法生成数据，仅用 SSE 包装。
     */
    @PostMapping(value = "/execute-step-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeStepStream(@RequestBody StepRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String sessionId = request.sessionId();
        String stepId = request.stepId();
        WorkflowState state = workflowStates.get(sessionId);

        CompletableFuture.runAsync(() -> {
            try {
                if (state == null) {
                    sseEvent(emitter, Map.of("type", "complete", "stepReply",
                            "未找到活跃的工作流，请重新发起分析。"));
                    emitter.complete();
                    return;
                }

                // 1. 流式推送进度
                List<String> msgs = STEP_PROGRESS.getOrDefault(stepId, List.of("正在处理..."));
                for (int i = 0; i < msgs.size(); i++) {
                    Thread.sleep(500 + (int) (Math.random() * 400));
                    int pct = (int) ((i + 1.0) / (msgs.size() + 1) * 100);
                    sseEvent(emitter, Map.of("type", "progress", "message", msgs.get(i), "percent", pct));
                }
                Thread.sleep(300);

                // 2. 执行步骤逻辑（复用已有方法）
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("sessionId", sessionId);
                switch (stepId) {
                    case "step1" -> executeStep1(state, result);
                    case "step2" -> executeStep2(state, result);
                    case "step3_1" -> executeStep3_1(state, result);
                    case "step3_2" -> executeStep3_2(state, result);
                    case "step3_3" -> executeStep3_3(state, result);
                    case "step3_4" -> executeStep3_4(state, result);
                    case "step4" -> executeStep4(state, result);
                    case "step5" -> executeStep5(state, result);
                    default -> result.put("stepReply", "未知步骤：" + stepId);
                }

                // 3. 推送 Excel 操作
                Object excelOps = result.get("excelOperations");
                if (excelOps != null) {
                    sseEvent(emitter, Map.of("type", "excel", "excelOperations", excelOps));
                }

                // 4. 推送完成
                sseEvent(emitter, Map.of("type", "complete",
                        "stepReply", result.getOrDefault("stepReply", "")));
                emitter.complete();
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /**
     * Agent驱动的工作流 —— 后端从"主skill"读取任务规划，逐步执行并通过SSE推送事件。
     * 事件类型：
     * - plan: 任务规划（包含所有步骤和子任务列表）
     * - step_start: 某个步骤开始执行
     * - sub_start: 某个子任务开始执行
     * - progress: 执行过程中的思考/进度信息
     * - excel: Excel操作数据
     * - sub_complete: 子任务完成
     * - step_complete: 步骤完成（含stepReply）
     * - confirm_needed: 需要用户确认（step4后）
     * - workflow_complete: 整个工作流完成
     */
    @PostMapping(value = "/execute-workflow-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowStream(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minutes timeout
        String sessionId = request.get("sessionId");
        WorkflowState state = workflowStates.get(sessionId);

        CompletableFuture.runAsync(() -> {
            try {
                if (state == null) {
                    sseEvent(emitter, Map.of("type", "error", "message", "未找到活跃的工作流，请重新发起分析。"));
                    emitter.complete();
                    return;
                }

                // ===== Phase 1: Agent读取主skill，规划任务 =====
                sseEvent(emitter, Map.of("type", "thinking", "message", "正在读取工作流规划..."));
                Thread.sleep(800);

                List<WorkflowStep> steps = workflowPlanService.getWorkflowSteps();

                // 发送任务规划
                List<Map<String, Object>> planSteps = new ArrayList<>();
                for (WorkflowStep step : steps) {
                    List<Map<String, String>> subs = new ArrayList<>();
                    for (SubTask sub : step.subTasks()) {
                        subs.add(Map.of("id", sub.id(), "text", sub.text()));
                    }
                    planSteps.add(Map.of("id", step.id(), "title", step.title(), "subs", subs));
                }
                sseEvent(emitter, Map.of("type", "plan", "steps", planSteps));
                Thread.sleep(500);

                // ===== Phase 2: 逐步执行每个任务 =====
                for (int i = 0; i < steps.size(); i++) {
                    WorkflowStep step = steps.get(i);
                    state.currentStep = i;

                    // Agent思考：决定执行当前步骤
                    sseEvent(emitter, Map.of("type", "thinking",
                            "message", "正在分析第" + (i + 1) + "步任务需求..."));
                    Thread.sleep(600 + (int) (Math.random() * 400));

                    // step_start
                    sseEvent(emitter, Map.of("type", "step_start", "stepId", step.id(),
                            "stepIndex", i, "title", step.title()));
                    Thread.sleep(300);

                    // 执行每个子任务
                    String lastStepReply = "";
                    for (int j = 0; j < step.subTasks().size(); j++) {
                        SubTask sub = step.subTasks().get(j);

                        // sub_start
                        sseEvent(emitter, Map.of("type", "sub_start",
                                "stepId", step.id(), "subId", sub.id()));
                        Thread.sleep(200);

                        // 流式进度
                        List<String> msgs = STEP_PROGRESS.getOrDefault(sub.id(), List.of("正在处理..."));
                        for (int k = 0; k < msgs.size(); k++) {
                            Thread.sleep(500 + (int) (Math.random() * 400));
                            int pct = (int) ((k + 1.0) / (msgs.size() + 1) * 100);
                            sseEvent(emitter, Map.of("type", "progress",
                                    "stepId", step.id(), "subId", sub.id(),
                                    "message", msgs.get(k), "percent", pct));
                        }
                        Thread.sleep(300);

                        // 执行步骤逻辑
                        Map<String, Object> result = new LinkedHashMap<>();
                        executeSubStep(sub.id(), state, result);

                        // 推送 Excel 操作
                        Object excelOps = result.get("excelOperations");
                        if (excelOps != null) {
                            sseEvent(emitter, Map.of("type", "excel",
                                    "stepId", step.id(), "subId", sub.id(),
                                    "excelOperations", excelOps));
                        }

                        // 记录最后一个子任务的 reply
                        if (result.containsKey("stepReply")) {
                            lastStepReply = (String) result.get("stepReply");
                        }

                        // sub_complete
                        sseEvent(emitter, Map.of("type", "sub_complete",
                                "stepId", step.id(), "subId", sub.id()));
                        Thread.sleep(200);
                    }

                    String stepReply = lastStepReply;

                    // Agent检查：任务是否完成
                    sseEvent(emitter, Map.of("type", "thinking",
                            "message", "正在检查第" + (i + 1) + "步执行结果..."));
                    Thread.sleep(400);

                    sseEvent(emitter, Map.of("type", "step_complete",
                            "stepId", step.id(), "stepIndex", i,
                            "stepReply", stepReply));

                    // step4后需要用户确认
                    if ("step4".equals(step.id())) {
                        sseEvent(emitter, Map.of("type", "confirm_needed",
                                "stepId", step.id(),
                                "message", "是否需要继续执行波动分析？"));

                        // 等待用户确认（最多5分钟）
                        CountDownLatch latch = new CountDownLatch(1);
                        state.confirmLatch = latch;
                        state.confirmResult = null;

                        boolean answered = latch.await(5, TimeUnit.MINUTES);
                        if (!answered || !Boolean.TRUE.equals(state.confirmResult)) {
                            sseEvent(emitter, Map.of("type", "workflow_stopped",
                                    "message", "用户选择不继续执行，工作流已停止。"));
                            emitter.complete();
                            return;
                        }

                        sseEvent(emitter, Map.of("type", "confirm_resolved",
                                "confirmed", true));
                        Thread.sleep(300);

                        // Agent决定继续
                        sseEvent(emitter, Map.of("type", "thinking",
                                "message", "用户确认继续，正在规划下一步任务..."));
                        Thread.sleep(500);
                    } else if (i < steps.size() - 1) {
                        // Agent决定下一步
                        sseEvent(emitter, Map.of("type", "thinking",
                                "message", "第" + (i + 1) + "步已完成，正在规划下一步..."));
                        Thread.sleep(500);
                    }
                }

                // ===== Phase 3: 全部完成 =====
                sseEvent(emitter, Map.of("type", "workflow_complete",
                        "message", "所有任务已完成！现金流报表波动合理性检查流程执行成功。"));
                state.phase = "completed";
                emitter.complete();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    /**
     * 用户确认接口 —— step4校验后，用户选择是否继续执行。
     */
    @PostMapping("/workflow-confirm")
    public Map<String, String> workflowConfirm(@RequestBody Map<String, Object> request) {
        String sessionId = (String) request.get("sessionId");
        Boolean confirmed = (Boolean) request.get("confirmed");

        WorkflowState state = workflowStates.get(sessionId);
        if (state != null && state.confirmLatch != null) {
            state.confirmResult = confirmed;
            state.confirmLatch.countDown();
            return Map.of("status", "ok");
        }
        return Map.of("status", "error", "message", "未找到等待确认的工作流");
    }

    private void executeSubStep(String subId, WorkflowState state, Map<String, Object> result) {
        switch (subId) {
            case "step1" -> executeStep1(state, result);
            case "step2" -> executeStep2(state, result);
            case "step3_1" -> executeStep3_1(state, result);
            case "step3_2" -> executeStep3_2(state, result);
            case "step3_3" -> executeStep3_3(state, result);
            case "step3_4" -> executeStep3_4(state, result);
            case "step4" -> executeStep4(state, result);
            case "step5" -> executeStep5(state, result);
            default -> result.put("stepReply", "未知步骤：" + subId);
        }
    }

    private void sseEvent(SseEmitter emitter, Map<String, Object> data) throws IOException {
        emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
    }

    private void executeStep1(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet1-报表数据");

        // 动态统计波动超20%的报表项
        long overCount = data.rows().stream()
                .filter(r -> "是".equals(r.get("是否波动超20%")))
                .count();
        StringBuilder reply = new StringBuilder("第一步已完成！波动超20%的报表项有" + overCount + "个：\n");
        data.rows().stream()
                .filter(r -> "是".equals(r.get("是否波动超20%")))
                .forEach(r -> reply.append("• ").append(r.get("报表项"))
                        .append("：波动比例 ").append(r.get("波动比例"))
                        .append("，现在对波动超20%的报表项进行明细数据分析并给出分析结论。\n"));

        result.put("stepReply", reply.toString().trim());
        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", data.name(),
                        "headers", data.headers(), "rows", data.rows())
        ));
    }

    private void executeStep2(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet2-DCF010102明细数据");

        result.put("stepReply", "第二步已完成！已成功将DCF010102明细数据（"
                + String.format("%,d", data.rows().size()) + "行 × " + data.headers().size() + "列）写入到"
                + data.name() + "中。");
        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", data.name(),
                        "headers", data.headers(), "rows", data.rows())
        ));
    }

    private void executeStep3_1(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet3-所有权数据");

        result.put("stepReply", "第3.1步完成！已成功获取股权信息并标注8大分层，共" + data.rows().size() + "条记录。");
        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", data.name(),
                        "headers", data.headers(), "rows", data.rows())
        ));
    }

    private void executeStep3_2(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet2-DCF010102明细数据");
        int total = data.rows().size();

        result.put("stepReply", "第3.2步完成！已成功关联明细数据与股权数据，标注公司层级和IC层级。\n"
                + "· 共处理" + total + "条明细记录");
    }

    private void executeStep3_3(WorkflowState state, Map<String, Object> result) {
        // 从 Sheet4 处理后数据中提取公司层级和IC层级，追加到 Sheet2
        MockDataService.SheetData sheet4 = mockDataService.getSheet("Sheet4-DCF010102处理后数据");
        MockDataService.SheetData sheet2 = mockDataService.getSheet("Sheet2-DCF010102明细数据");

        List<String> appendHeaders = List.of("公司层级", "IC层级");
        List<Map<String, String>> appendRows = new ArrayList<>();
        int rowCount = sheet2.rows().size();
        for (int i = 0; i < rowCount && i < sheet4.rows().size(); i++) {
            Map<String, String> row = sheet4.rows().get(i);
            Map<String, String> appendRow = new LinkedHashMap<>();
            appendRow.put("公司层级", row.getOrDefault("公司层级", ""));
            appendRow.put("IC层级", row.getOrDefault("IC层级", ""));
            appendRows.add(appendRow);
        }

        result.put("stepReply", "第3.3步完成！已成功更新包含SR数据的IC层级。在明细数据中新增2列：公司层级、IC层级。");
        result.put("excelOperations", List.of(
                Map.of("action", "updateSheet", "sheetIndex", 1,
                        "headers", appendHeaders, "rows", appendRows, "append", true)
        ));
    }

    private void executeStep3_4(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet4-DCF010102处理后数据");

        result.put("stepReply", "第三步已完成！已成功根据公司层级、IC层级及JC过滤处理数据，DCF010102明细数据处理后数据已放置于"
                + data.name() + "。");
        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", data.name(),
                        "headers", data.headers(), "rows", data.rows())
        ));
    }

    private void executeStep4(WorkflowState state, Map<String, Object> result) {
        result.put("stepReply", "步骤4完成！校验结果：\n"
                + "结论：在亿级上，两个值不一致，相差1.1亿，这个差异可能是因为：\n"
                + "1. 合并报告数据经过了分录调整，该差异为合理性差异？\n"
                + "2. 数据源或者取数口径存在差异，需要进一步确认");
    }

    private void executeStep5(WorkflowState state, Map<String, Object> result) {
        MockDataService.SheetData data = mockDataService.getSheet("Sheet5-现金流报表分析结论");

        result.put("stepReply", "第五步完成！按简化场景汇聚并分析波动，已创建结论分析Sheet。\n\n"
                + "✅ 完成！现金流报表分析流程全部执行成功。\n"
                + "共分析" + data.rows().size() + "个波动报表项，结论已写入「现金流报表分析结论」Sheet。");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", data.name(),
                        "headers", data.headers(), "rows", data.rows())
        ));

        state.phase = "completed";
    }

    @PostMapping("/clear")
    public Map<String, String> clearContext(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        if (sessionId != null) {
            contextManager.clearContext(sessionId);
            memoryManager.clearSession(sessionId);
            workflowStates.remove(sessionId);
        }
        return Map.of("status", "ok", "message", "会话已清除");
    }

    @GetMapping("/welcome")
    public Map<String, String> welcome() {
        return Map.of("message", "您好！我是财报波动合理性检查智能助手");
    }

    // ===================== Helper Methods =====================

    private boolean isAnalysisTrigger(String message) {
        return message.contains("现金流") || message.contains("波动")
                || message.contains("报表") && message.contains("分析")
                || message.contains("资产负债") && message.contains("检查");
    }

    private boolean isEntityResponse(String sessionId, String message) {
        WorkflowState state = workflowStates.get(sessionId);
        return state != null && "awaiting_entity".equals(state.phase);
    }

    private String fallbackChat(String userMessage, List<MemoryEntry> memories) {
        try {
            StringBuilder systemPrompt = new StringBuilder(
                    "你是一个财报波动合理性分析助手，请帮助用户分析报表项目。请用中文回复。");
            if (!memories.isEmpty()) {
                systemPrompt.append("\n\n对话记忆：\n");
                memories.forEach(m -> systemPrompt.append("- ").append(m.key()).append(": ").append(m.value()).append("\n"));
            }
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt.toString()));
            messages.add(new UserMessage(userMessage));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            return "抱歉，当前无法处理您的请求，请稍后再试。";
        }
    }

    public record ChatRequest(String sessionId, String message) {}
    public record StepRequest(String sessionId, String stepId) {}

    private static class WorkflowState {
        String entity;
        String phase = "idle";
        int currentStep = 0;
        volatile CountDownLatch confirmLatch;
        volatile Boolean confirmResult;
    }
}
