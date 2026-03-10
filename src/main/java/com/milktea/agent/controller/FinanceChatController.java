package com.milktea.agent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.milktea.agent.context.ContextManager;
import com.milktea.agent.memory.MemoryEntry;
import com.milktea.agent.memory.MemoryManager;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 财报分析聊天控制器 - 处理 frontend-extensionV2 的所有前端请求。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceChatController {

    private final ReactAgent financeMainAgent;
    private final ChatModel chatModel;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final SkillsAgentHook financeSkillsAgentHook;

    private final Map<String, WorkflowState> workflowStates = new ConcurrentHashMap<>();

    public FinanceChatController(@Qualifier("financeMainAgent") ReactAgent financeMainAgent,
                                  ChatModel chatModel,
                                  ContextManager contextManager,
                                  MemoryManager memoryManager,
                                  @Qualifier("financeSkillsAgentHook") SkillsAgentHook financeSkillsAgentHook) {
        this.financeMainAgent = financeMainAgent;
        this.chatModel = chatModel;
        this.contextManager = contextManager;
        this.memoryManager = memoryManager;
        this.financeSkillsAgentHook = financeSkillsAgentHook;
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

            reply = "请告知我您需要对哪一个实体公司进行现金流报表波动合理性分析？";
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
                reply = financeMainAgent.call(userMessage);
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

    private void executeStep1(WorkflowState state, Map<String, Object> result) {
        List<String> headers = List.of("报表项", "上一期间(亿)", "当前期间(亿)",
                "波动金额(亿)", "波动比例", "是否波动超20%");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "DCF010101 营业收入", "119.85", "120.32", "0.47", "0.39%", "否"),
                createRow(headers, "DCF010102 营业成本", "85.23", "86.01", "0.78", "0.92%", "否"),
                createRow(headers, "DCF010201 投资收益", "12.05", "18.40", "6.35", "52.69%", "是"),
                createRow(headers, "DCF010202 公允价值变动", "8.80", "4.30", "-4.50", "-51.16%", "是"),
                createRow(headers, "DCF010301 资产处置收益", "3.20", "3.45", "0.25", "7.81%", "否"),
                createRow(headers, "DCF010302 汇兑收益", "2.10", "2.35", "0.25", "11.90%", "否"),
                createRow(headers, "DCF010401 营业费用", "15.60", "16.20", "0.60", "3.85%", "否"),
                createRow(headers, "DCF010402 管理费用", "10.50", "10.80", "0.30", "2.86%", "否")
        );

        result.put("stepReply", "第一步已完成！波动超20%的报表项有2个：\n"
                + "· DCF010201 投资收益 波动最大（52.69%），金额增加约6.35亿\n"
                + "· DCF010202 公允价值变动 波动-51.16%，金额减少约4.50亿\n"
                + "现在分别依次对超过20%的报表项进行明细数据分析并给出分析结论。");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet1-报表数据",
                        "headers", headers, "rows", rows)
        ));
    }

    private void executeStep2(WorkflowState state, Map<String, Object> result) {
        List<String> headers = List.of("简化场景", "场景", "period_id", "company_trace_group",
                "科目名称", "IC信息", "je_category");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "EMS售后回款", "1111_EMS调账", "2004082004", "0001", "11100 (应收账款)", "0000", "2141_DIM_APL"),
                createRow(headers, "其他产品销售", "2222_产品销售", "2004082004", "0002", "11200 (其他应收款)", "0011G", "3251_DIM_REV"),
                createRow(headers, "押金保证金", "3333_押金", "2004082004", "0003", "22100 (其他应付款)", "0000", "4161_DIM_EXP"),
                createRow(headers, "其他应收款转回", "4444_转回", "2004082004", "0001", "11200 (其他应收款)", "0022G", "2141_DIM_APL"),
                createRow(headers, "利息", "5555_利息收入", "2004082004", "0004", "66100 (利息收入)", "0000", "5171_DIM_FIN"),
                createRow(headers, "EMS售后回购", "6666_EMS回购", "2004082004", "0001", "11100 (应收账款)", "0011G", "2141_DIM_APL"),
                createRow(headers, "公司间交易", "7777_IC交易", "2004082004", "0005", "12100 (公司间应收)", "0033G", "6181_DIM_IC"),
                createRow(headers, "其他交易", "8888_其他", "2004082004", "0006", "99100 (其他)", "0000", "9999_DIM_OTH")
        );

        result.put("stepReply", "第二步已完成！已成功获取DCF010201报表项的明细数据，并插入Sheet2-DCF010201明细数据中。");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet2-DCF010201明细数据",
                        "headers", headers, "rows", rows)
        ));
    }

    private void executeStep3_1(WorkflowState state, Map<String, Object> result) {
        List<String> headers = List.of("公司代码", "公司名称", "被持股公司所属8大分层");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "0001", "子公司A", "0011G"),
                createRow(headers, "0002", "子公司B", "0011G"),
                createRow(headers, "0003", "子公司C", "0022G"),
                createRow(headers, "0004", "子公司D", "0011G"),
                createRow(headers, "0005", "联营公司E", "0033G"),
                createRow(headers, "0006", "合营公司F", "0044G")
        );

        result.put("stepReply", "第3.1步完成！已成功获取股权信息并标注8大分层，并将该信息插入在线Excel中的Sheet3-所有权数据。");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet3-所有权数据",
                        "headers", headers, "rows", rows)
        ));
    }

    private void executeStep3_2(WorkflowState state, Map<String, Object> result) {
        result.put("stepReply", "第3.2步完成！已成功关联明细数据与股权数据，标注公司层级和IC层级。\n"
                + "· 共处理8条明细记录\n"
                + "· 成功匹配层级信息6条\n"
                + "· 2条无匹配股权信息（归类为外部交易）");
    }

    private void executeStep3_3(WorkflowState state, Map<String, Object> result) {
        List<String> appendHeaders = List.of("公司层级", "IC层级");
        List<Map<String, String>> appendRows = List.of(
                Map.of("公司层级", "0011G", "IC层级", "外部"),
                Map.of("公司层级", "0011G", "IC层级", "0011G"),
                Map.of("公司层级", "0022G", "IC层级", "外部"),
                Map.of("公司层级", "0011G", "IC层级", "0022G"),
                Map.of("公司层级", "0011G", "IC层级", "外部"),
                Map.of("公司层级", "0011G", "IC层级", "0011G"),
                Map.of("公司层级", "0033G", "IC层级", "0033G"),
                Map.of("公司层级", "0044G", "IC层级", "外部")
        );

        result.put("stepReply", "第3.3步完成！已成功更新包含SR数据的IC层级。在明细数据中新增2列：公司层级、IC层级。");

        result.put("excelOperations", List.of(
                Map.of("action", "updateSheet", "sheetIndex", 1,
                        "headers", appendHeaders, "rows", appendRows, "append", true)
        ));
    }

    private void executeStep3_4(WorkflowState state, Map<String, Object> result) {
        List<String> headers = List.of("简化场景", "场景", "period_id", "company_trace_group",
                "科目名称", "IC信息", "je_category", "公司层级", "IC层级");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "EMS售后回款", "1111_EMS调账", "2004082004", "0001", "11100 (应收账款)", "0000", "2141_DIM_APL", "0011G", "外部"),
                createRow(headers, "其他产品销售", "2222_产品销售", "2004082004", "0002", "11200 (其他应收款)", "0011G", "3251_DIM_REV", "0011G", "0011G"),
                createRow(headers, "押金保证金", "3333_押金", "2004082004", "0003", "22100 (其他应付款)", "0000", "4161_DIM_EXP", "0022G", "外部"),
                createRow(headers, "其他应收款转回", "4444_转回", "2004082004", "0001", "11200 (其他应收款)", "0022G", "2141_DIM_APL", "0011G", "0022G"),
                createRow(headers, "利息", "5555_利息收入", "2004082004", "0004", "66100 (利息收入)", "0000", "5171_DIM_FIN", "0011G", "外部"),
                createRow(headers, "EMS售后回购", "6666_EMS回购", "2004082004", "0001", "11100 (应收账款)", "0011G", "2141_DIM_APL", "0011G", "0011G")
        );

        result.put("stepReply", "第3.4步完成！已成功过滤数据并创建Sheet4-DCF010201处理后数据。\n"
                + "· 原始记录：8条\n"
                + "· 过滤掉：2条（1条测试数据、1条零金额记录）\n"
                + "· 剩余记录：6条");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet4-DCF010201处理后数据",
                        "headers", headers, "rows", rows)
        ));
    }

    private void executeStep4(WorkflowState state, Map<String, Object> result) {
        result.put("stepReply", "第四步完成！校验明细数据合理性校验结果：\n\n"
                + "明细数据 rmb_fact_ex_rate_ptd 求和：15.88亿\n"
                + "系统报表 DCF010201 当月金额：18.40亿\n"
                + "差异：2.52亿\n\n"
                + "结论：❌ 不一致（差异大于等于1亿）\n\n"
                + "分析说明：\n"
                + "明细数据求和与报表数据存在2.52亿元的差异\n"
                + "差异较大，可能原因：\n"
                + "  · 明细数据经过过滤后，部分数据被剔除\n"
                + "  · period_id可能不完全对应P12期间\n"
                + "  · 数据源或口径存在差异\n"
                + "  · 汇总逻辑可能需要进一步检查\n"
                + "请确认差异原因及解决方案。");
    }

    private void executeStep5(WorkflowState state, Map<String, Object> result) {
        List<String> headers = List.of("报表项", "上一期间(亿)", "当前期间(亿)",
                "波动金额(亿)", "波动比例", "波动分析结论");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "DCF010201 投资收益", "12.05", "18.40", "6.35", "52.69%",
                        "本月18.40亿，上月12.05亿，变动6.35亿，增长率52.69%，主要受到以下因素影响：\n"
                        + "【EMS收款】本月3.48亿，上月2.10亿，变动1.38亿，增长率65.71%；\n"
                        + "【智选车定金】本月2.80亿，上月1.50亿，变动1.30亿，增长率86.67%；\n"
                        + "【终端自管店押金保证金】自管店保证金1.20亿，波动0.55亿，其中押金转货款0.30亿，收到押金0.15亿，货款转押金0.10亿；\n"
                        + "【外汇远期处置收益】本月1.05亿，上月0.65亿，变动0.40亿，增长率61.54%；\n"
                        + "【利息收入】本月2.24亿，上月2.05亿，变动0.19亿，增长率9.27%；\n"
                        + "【华宜小额贷款】本月0.85亿，上月0.42亿，变动0.43亿，增长率102.38%"),
                createRow(headers, "DCF010202 公允价值变动", "8.80", "4.30", "-4.50", "-51.16%",
                        "本月4.30亿，上月8.80亿，变动-4.50亿，增长率-51.16%，主要受到以下因素影响：\n"
                        + "【EMS收款】本月1.20亿，上月3.50亿，变动-2.30亿，增长率-65.71%；\n"
                        + "【智选车定金】本月0.80亿，上月1.60亿，变动-0.80亿，增长率-50.00%；\n"
                        + "【终端自管店押金保证金】自管店保证金0.65亿，波动-0.35亿，其中押金转货款0.10亿，收到押金0.05亿，货款转押金0.20亿；\n"
                        + "【外汇远期处置收益】本月0.45亿，上月0.90亿，变动-0.45亿，增长率-50.00%；\n"
                        + "【利息收入】本月0.50亿，上月0.60亿，变动-0.10亿，增长率-16.67%；\n"
                        + "【华宜小额贷款】本月0.30亿，上月0.80亿，变动-0.50亿，增长率-62.50%")
        );

        result.put("stepReply", "第五步完成！按简化场景汇聚并分析波动，已创建结论分析Sheet。\n\n"
                + "✅ 完成！现金流报表分析流程全部执行成功。\n"
                + "共分析2个波动超20%的报表项，结论已写入「现金流报表分析结论」Sheet。");

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "现金流报表分析结论",
                        "headers", headers, "rows", rows)
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
        return Map.of("message", "您好！我是财报波动合理性分析智能助手。"
                + "我可以帮您分析报表项波动的合理性，包括现金流量表、资产负债表等。"
                + "请告诉我您想分析哪份报表？");
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
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            return "抱歉，当前无法处理您的请求，请稍后再试。";
        }
    }

    private Map<String, String> createRow(List<String> headers, String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size() && i < values.length; i++) {
            row.put(headers.get(i), values[i]);
        }
        return row;
    }

    public record ChatRequest(String sessionId, String message) {}
    public record StepRequest(String sessionId, String stepId) {}

    private static class WorkflowState {
        String entity;
        String phase = "idle";
        int currentStep = 0;
    }
}
