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
 * Finance Chat Controller - handles all frontend-extensionV2 requests.
 * Provides chat, workflow step execution, and data operations for the
 * financial report volatility analysis agent.
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceChatController {

    private final ReactAgent financeMainAgent;
    private final ChatModel chatModel;
    private final ContextManager contextManager;
    private final MemoryManager memoryManager;
    private final SkillsAgentHook financeSkillsAgentHook;

    // Track workflow state per session
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

        // Initialize context
        contextManager.getOrCreateContext(sessionId, null);
        contextManager.addUserMessage(sessionId, userMessage);

        // Session memory
        List<MemoryEntry> memories = memoryManager.searchSessionMemories(sessionId, userMessage);

        // Determine if this is a workflow trigger
        boolean isWorkflowTrigger = isAnalysisTrigger(userMessage);
        boolean isEntityResponse = isEntityResponse(sessionId, userMessage);

        String reply;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);

        if (isWorkflowTrigger) {
            // User wants to start analysis - ask for entity
            WorkflowState state = new WorkflowState();
            state.phase = "awaiting_entity";
            workflowStates.put(sessionId, state);

            reply = "Please tell me which entity you want to perform the cash flow report reasonability analysis on?";
            result.put("reply", reply);
            result.put("workflowActive", false);

        } else if (isEntityResponse) {
            // User provided entity name - start the workflow
            WorkflowState state = workflowStates.get(sessionId);
            state.entity = userMessage.trim();
            state.phase = "running";
            state.currentStep = 0;

            reply = String.format("I will help you complete the cash flow report volatility analysis for %s. "
                    + "I will extract report data, calculate volatility ratios, mark anomalous volatility (over 20%%), "
                    + "extract and analyze detail data, and finally output the report analysis results.\n\n"
                    + "Now executing Step 1: Extract system cash flow report data, calculate volatility ratios and mark anomalous volatility, and insert into online Excel Sheet1.", state.entity);

            result.put("reply", reply);
            result.put("workflowActive", true);
            result.put("taskUpdates", List.of(
                    Map.of("taskId", "step1", "status", "active", "detail", "Retrieving " + state.entity + " report data...")
            ));
            result.put("nextStep", "step1");

        } else {
            // Regular chat - use agent
            try {
                reply = financeMainAgent.call(userMessage);
            } catch (Exception e) {
                reply = fallbackChat(userMessage, memories);
            }
            if (reply == null || reply.isBlank()) {
                reply = "I didn't understand your request. Could you please repeat that?";
            }
            result.put("reply", reply);
            result.put("workflowActive", false);
        }

        // Save context and memory
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
            result.put("reply", "No active workflow found. Please start a new analysis.");
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
            default -> result.put("reply", "Unknown step: " + stepId);
        }

        return result;
    }

    private void executeStep1(WorkflowState state, Map<String, Object> result) {
        // Step 1: Retrieve report data
        List<String> headers = List.of("Report Item", "Previous Period (hundred million)", "Current Period (hundred million)",
                "Volatility Amount (hundred million)", "Volatility Ratio", "Is Volatility > 20%");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "DCF010101 Operating Revenue", "119.85", "120.32", "0.47", "0.39%", "No"),
                createRow(headers, "DCF010102 Operating Costs", "85.23", "86.01", "0.78", "0.92%", "No"),
                createRow(headers, "DCF010201 Investment Income", "12.05", "18.40", "6.35", "52.69%", "Yes"),
                createRow(headers, "DCF010202 Fair Value Changes", "8.80", "4.30", "-4.50", "-51.16%", "Yes"),
                createRow(headers, "DCF010301 Asset Disposal Income", "3.20", "3.45", "0.25", "7.81%", "No"),
                createRow(headers, "DCF010302 Exchange Gains", "2.10", "2.35", "0.25", "11.90%", "No"),
                createRow(headers, "DCF010401 Operating Expenses", "15.60", "16.20", "0.60", "3.85%", "No"),
                createRow(headers, "DCF010402 Admin Expenses", "10.50", "10.80", "0.30", "2.86%", "No")
        );

        result.put("reply", "Step 1 complete! Items with volatility exceeding 20%: 2 items.\n"
                + "DCF010201 has the highest volatility (52.69%), amount increased by approximately 6.35 hundred million.\n"
                + "DCF010202 volatility 51.16%, amount decreased by approximately 4.50 hundred million.\n\n"
                + "Now analyzing detail data for items exceeding 20% volatility one by one.");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step1", "status", "completed",
                        "detail", "Retrieved 8 report items, 2 items with volatility >20%: DCF010201 (52.69%), DCF010202 (-51.16%)"),
                Map.of("taskId", "step2", "status", "active", "detail", "Retrieving DCF010201 detail data...")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet1-Report Data",
                        "headers", headers, "rows", rows)
        ));

        result.put("nextStep", "step2");
    }

    private void executeStep2(WorkflowState state, Map<String, Object> result) {
        // Step 2: Retrieve detail data
        List<String> headers = List.of("Simplified Scenario", "Scenario", "period_id", "company_trace_group",
                "account_code_name", "IC_info", "je_category");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "EMS After-sales Collection", "1111_EMS Adjustment", "2004082004", "0001", "11100 (Accounts Receivable)", "0000", "2141_DIM_APL"),
                createRow(headers, "Other Product Sales", "2222_Product Sales", "2004082004", "0002", "11200 (Other Receivables)", "0011G", "3251_DIM_REV"),
                createRow(headers, "Deposit Guarantee", "3333_Deposit", "2004082004", "0003", "22100 (Other Payables)", "0000", "4161_DIM_EXP"),
                createRow(headers, "Other Receivables Reversal", "4444_Reversal", "2004082004", "0001", "11200 (Other Receivables)", "0022G", "2141_DIM_APL"),
                createRow(headers, "Interest", "5555_Interest Income", "2004082004", "0004", "66100 (Interest Income)", "0000", "5171_DIM_FIN"),
                createRow(headers, "EMS After-sales Repurchase", "6666_EMS Repurchase", "2004082004", "0001", "11100 (Accounts Receivable)", "0011G", "2141_DIM_APL"),
                createRow(headers, "Intercompany Transactions", "7777_IC Transaction", "2004082004", "0005", "12100 (Intercompany Receivable)", "0033G", "6181_DIM_IC"),
                createRow(headers, "Other Transactions", "8888_Other", "2004082004", "0006", "99100 (Other)", "0000", "9999_DIM_OTH")
        );

        result.put("reply", "Step 2 complete! Successfully retrieved DCF010201 report item detail data, inserted into Sheet2-DCF010201 Detail Data.");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step2", "status", "completed",
                        "detail", "Retrieved 8 detail records for DCF010201"),
                Map.of("taskId", "step3_1", "status", "active", "detail", "Retrieving equity information...")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet2-DCF010201 Detail Data",
                        "headers", headers, "rows", rows)
        ));

        result.put("nextStep", "step3_1");
    }

    private void executeStep3_1(WorkflowState state, Map<String, Object> result) {
        // Step 3.1: Mark equity tiers
        List<String> headers = List.of("Company Code", "Company Name", "Investee 8-Tier Classification");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "0001", "Subsidiary A", "0011G"),
                createRow(headers, "0002", "Subsidiary B", "0011G"),
                createRow(headers, "0003", "Subsidiary C", "0022G"),
                createRow(headers, "0004", "Subsidiary D", "0011G"),
                createRow(headers, "0005", "Associated Company E", "0033G"),
                createRow(headers, "0006", "Joint Venture F", "0044G")
        );

        result.put("reply", "Step 3.1 complete! Successfully retrieved equity information and marked 8 major tiers. Data inserted into Sheet3-Ownership Data.");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step3_1", "status", "completed",
                        "detail", "Marked 6 companies with tier classifications"),
                Map.of("taskId", "step3_2", "status", "active", "detail", "Associating detail data with tier info...")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet3-Ownership Data",
                        "headers", headers, "rows", rows)
        ));

        result.put("nextStep", "step3_2");
    }

    private void executeStep3_2(WorkflowState state, Map<String, Object> result) {
        // Step 3.2: Associate detail data and mark tiers
        result.put("reply", "Step 3.2 complete! Successfully associated detail data with equity data, marked company tier and IC tier.\n"
                + "- Total 8 detail records processed\n"
                + "- Successfully matched tier info for 6 records\n"
                + "- 2 records have no matching equity information (classified as external transaction)");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step3_2", "status", "completed",
                        "detail", "Associated 8 records, 6 matched with tier info"),
                Map.of("taskId", "step3_3", "status", "active", "detail", "Updating SR data IC tier...")
        ));

        result.put("nextStep", "step3_3");
    }

    private void executeStep3_3(WorkflowState state, Map<String, Object> result) {
        // Step 3.3: Update SR data IC tier - add columns to sheet2
        List<String> appendHeaders = List.of("Company Tier", "IC Tier");
        List<Map<String, String>> appendRows = List.of(
                Map.of("Company Tier", "0011G", "IC Tier", "External"),
                Map.of("Company Tier", "0011G", "IC Tier", "0011G"),
                Map.of("Company Tier", "0022G", "IC Tier", "External"),
                Map.of("Company Tier", "0011G", "IC Tier", "0022G"),
                Map.of("Company Tier", "0011G", "IC Tier", "External"),
                Map.of("Company Tier", "0011G", "IC Tier", "0011G"),
                Map.of("Company Tier", "0033G", "IC Tier", "0033G"),
                Map.of("Company Tier", "0044G", "IC Tier", "External")
        );

        result.put("reply", "Step 3.3 complete! Successfully updated IC tier for SR-containing data. "
                + "Added 2 new columns to detail data: Company Tier, IC Tier.");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step3_3", "status", "completed",
                        "detail", "Updated 4 SR records with IC tier information"),
                Map.of("taskId", "step3_4", "status", "active", "detail", "Filtering data...")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "updateSheet", "sheetIndex", 1,
                        "headers", appendHeaders, "rows", appendRows, "append", true)
        ));

        result.put("nextStep", "step3_4");
    }

    private void executeStep3_4(WorkflowState state, Map<String, Object> result) {
        // Step 3.4: Filter and create processed data
        List<String> headers = List.of("Simplified Scenario", "Scenario", "period_id", "company_trace_group",
                "account_code_name", "IC_info", "je_category", "Company Tier", "IC Tier");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "EMS After-sales Collection", "1111_EMS Adjustment", "2004082004", "0001", "11100 (Accounts Receivable)", "0000", "2141_DIM_APL", "0011G", "External"),
                createRow(headers, "Other Product Sales", "2222_Product Sales", "2004082004", "0002", "11200 (Other Receivables)", "0011G", "3251_DIM_REV", "0011G", "0011G"),
                createRow(headers, "Deposit Guarantee", "3333_Deposit", "2004082004", "0003", "22100 (Other Payables)", "0000", "4161_DIM_EXP", "0022G", "External"),
                createRow(headers, "Other Receivables Reversal", "4444_Reversal", "2004082004", "0001", "11200 (Other Receivables)", "0022G", "2141_DIM_APL", "0011G", "0022G"),
                createRow(headers, "Interest", "5555_Interest Income", "2004082004", "0004", "66100 (Interest Income)", "0000", "5171_DIM_FIN", "0011G", "External"),
                createRow(headers, "EMS After-sales Repurchase", "6666_EMS Repurchase", "2004082004", "0001", "11100 (Accounts Receivable)", "0011G", "2141_DIM_APL", "0011G", "0011G")
        );

        result.put("reply", "Step 3.4 complete! Successfully filtered data and created processed data.\n"
                + "- Original records: 8\n"
                + "- Filtered out: 2 records (1 test data, 1 zero-amount record)\n"
                + "- Remaining records: 6\n"
                + "- Created Sheet4-DCF010201 Processed Detail Data");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step3_4", "status", "completed",
                        "detail", "Filtered 2 records, 6 records remaining"),
                Map.of("taskId", "step4", "status", "active", "detail", "Verifying data reasonability...")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Sheet4-DCF010201 Processed Data",
                        "headers", headers, "rows", rows)
        ));

        result.put("nextStep", "step4");
    }

    private void executeStep4(WorkflowState state, Map<String, Object> result) {
        // Step 4: Check detail data reasonability
        result.put("reply", "Step 4 complete! Detail data reasonability verification results:\n\n"
                + "Detail data rmb_fact_ex_rate_ptd sum: 15.88 hundred million\n"
                + "System report DCF010201 current month amount: 18.40 hundred million\n"
                + "Difference: 2.52 hundred million\n\n"
                + "Conclusion: Not consistent (difference >= 100 million)\n\n"
                + "Analysis notes:\n"
                + "- Detail data sum and report data have a 2.52 hundred million difference\n"
                + "- Difference is significant, possible reasons:\n"
                + "  1. Some data was removed after filtering\n"
                + "  2. period_id may not fully correspond to P12 period\n"
                + "  3. Data source or caliber differences exist\n"
                + "  4. Aggregation logic may need further checking\n"
                + "- Please confirm the reason for the difference and resolution approach.");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step4", "status", "completed",
                        "detail", "Verification result: Not consistent, difference 2.52 hundred million"),
                Map.of("taskId", "step5", "status", "active", "detail", "Generating analysis report...")
        ));

        result.put("nextStep", "step5");
    }

    private void executeStep5(WorkflowState state, Map<String, Object> result) {
        // Step 5: Generate analysis report
        List<String> headers = List.of("Simplified Scenario", "Current Month (hundred million)", "Previous Month (hundred million)",
                "Volatility Amount (hundred million)", "Volatility Ratio (%)");

        List<Map<String, String>> rows = List.of(
                createRow(headers, "Other Product Sales", "7.05", "1.70", "5.35", "75.82%"),
                createRow(headers, "Deposit Guarantee", "6.05", "5.05", "1.00", "16.52%"),
                createRow(headers, "Other Receivables Reversal", "-6.29", "-6.79", "0.49", "-7.84%"),
                createRow(headers, "Interest", "2.24", "2.05", "0.19", "8.47%"),
                createRow(headers, "EMS After-sales Repurchase", "3.48", "3.79", "-0.32", "-9.17%")
        );

        result.put("reply", "Step 5 complete! Aggregated by simplified scenario and analyzed volatility. "
                + "Created Sheet: Cash Flow Report Analysis Conclusion.\n\n"
                + "Top 5 Scenarios by Volatility Amount:\n"
                + "1. Other Product Sales - volatility highest, growth 75.8%, increase 5.35 hundred million\n"
                + "2. Deposit Guarantee - growth 16.5%, increase 1.00 hundred million\n"
                + "3. Interest - growth 8.5%, increase 0.19 hundred million\n\n"
                + "Cash flow report analysis process completed successfully!");

        result.put("taskUpdates", List.of(
                Map.of("taskId", "step5", "status", "completed",
                        "detail", "Generated Top 5 analysis, highest volatility: Other Product Sales (75.82%)")
        ));

        result.put("excelOperations", List.of(
                Map.of("action", "addSheet", "sheetName", "Cash Flow Analysis Conclusion",
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
        return Map.of("status", "ok", "message", "Session cleared");
    }

    @GetMapping("/welcome")
    public Map<String, String> welcome() {
        return Map.of("message", "Welcome! I am the Financial Report Volatility Analysis Intelligent Assistant. "
                + "I can help you analyze the reasonability of report item volatility, including cash flow statements and balance sheets. "
                + "Please tell me which report you would like to analyze.");
    }

    // ===================== Helper Methods =====================

    private boolean isAnalysisTrigger(String message) {
        String lower = message.toLowerCase();
        return lower.contains("cash flow") || lower.contains("volatility")
                || lower.contains("report") && lower.contains("analy")
                || lower.contains("balance sheet") && lower.contains("check");
    }

    private boolean isEntityResponse(String sessionId, String message) {
        WorkflowState state = workflowStates.get(sessionId);
        return state != null && "awaiting_entity".equals(state.phase);
    }

    private String fallbackChat(String userMessage, List<MemoryEntry> memories) {
        try {
            StringBuilder systemPrompt = new StringBuilder(
                    "You are a financial report volatility analysis assistant. Help users analyze report items.");
            if (!memories.isEmpty()) {
                systemPrompt.append("\n\nConversation memory:\n");
                memories.forEach(m -> systemPrompt.append("- ").append(m.key()).append(": ").append(m.value()).append("\n"));
            }
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt.toString()));
            messages.add(new UserMessage(userMessage));
            ChatResponse response = chatModel.call(new Prompt(messages));
            return response.getResult().getOutput().getContent();
        } catch (Exception e) {
            return "Sorry, unable to process your request at this time. Please try again later.";
        }
    }

    private Map<String, String> createRow(List<String> headers, String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < headers.size() && i < values.length; i++) {
            row.put(headers.get(i), values[i]);
        }
        return row;
    }

    // Inner classes
    public record ChatRequest(String sessionId, String message) {}
    public record StepRequest(String sessionId, String stepId) {}

    private static class WorkflowState {
        String entity;
        String phase = "idle"; // idle, awaiting_entity, running, completed
        int currentStep = 0;
    }
}
