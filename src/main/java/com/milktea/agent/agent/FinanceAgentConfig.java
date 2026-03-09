package com.milktea.agent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.saver.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Configures the Finance Report Volatility Analysis Agent ecosystem using spring-ai-alibaba-agent-framework.
 * Uses Spring AI Alibaba Graph (com.alibaba.cloud.ai.graph) to implement a multi-step workflow agent.
 *
 * Workflow Steps:
 * 1. Retrieve EFM report data and calculate volatility (read-system-data)
 * 2. Retrieve DCF010102 detail data (read-account-detail-data)
 * 3.1 Retrieve equity info and mark 8 tiers (mark-level)
 * 3.2 Associate detail data and mark tiers (mark-detail-level)
 * 3.3 Update SR data IC tier (mark-ic-level)
 * 3.4 Filter and create processed data (filter-detail-data)
 * 4. Verify detail data reasonability (check-detail-data)
 * 5. Aggregate by simplified scenario and analyze volatility (generate-report)
 */
@Configuration
public class FinanceAgentConfig {

    private static final String FINANCE_MAIN_INSTRUCTION = """
            You are an AI intelligent assistant for financial report volatility analysis. Your responsibilities include:
            1. Help users analyze financial report item volatility reasonability
            2. Extract report data, calculate volatility ratios, and mark anomalous volatility (over 20%)
            3. Retrieve detail data for anomalous items for in-depth analysis
            4. Perform data processing: equity marking, tier association, IC tier update, data filtering
            5. Verify data reasonability
            6. Generate analysis reports and conclusions

            Please use available Skills to determine which skill to use based on the current workflow step.
            When you need to use a skill, first load the detailed instructions via the read_skill tool, then execute accordingly.

            【Workflow Steps】
            Step 1: Retrieve EFM report data and calculate volatility (Skill: read-system-data)
            Step 2: Retrieve detail data for items with >20% volatility (Skill: read-account-detail-data)
            Step 3.1: Retrieve equity info and mark 8 tiers (Skill: mark-level)
            Step 3.2: Associate detail data and mark tiers (Skill: mark-detail-level)
            Step 3.3: Update SR data IC tier (Skill: mark-ic-level)
            Step 3.4: Filter and create processed data (Skill: filter-detail-data)
            Step 4: Verify detail data reasonability (Skill: check-detail-data)
            Step 5: Aggregate by simplified scenario and analyze volatility (Skill: generate-report)

            【Interaction Rules】
            1. Before starting analysis, ask the user which entity company they want to analyze
            2. Show task planning list and update progress during execution
            3. Insert results into online Excel at each step for data visualization
            4. After completing all steps, output the complete analysis conclusion
            5. If the user asks questions during analysis, answer while maintaining workflow state
            """;

    private static final String DATA_RETRIEVAL_INSTRUCTION = """
            You are the data retrieval sub-agent. Responsible for:
            1. Retrieving system report data using read-system-data skill
            2. Calculating volatility values and marking items exceeding 20%
            3. Retrieving detail data for anomalous items using read-account-detail-data skill

            Report data result: {report_data_result}
            Execute the data retrieval steps and output the results.
            """;

    private static final String DATA_PROCESSING_INSTRUCTION = """
            You are the data processing sub-agent. Responsible for:
            1. Retrieving equity info and marking 8 tiers (mark-level)
            2. Associating detail data and marking tiers (mark-detail-level)
            3. Updating SR data IC tier (mark-ic-level)
            4. Filtering and creating processed data (filter-detail-data)

            Report data: {report_data_result}
            Detail data: {detail_data_result}
            Execute the data processing steps and output the results.
            """;

    private static final String ANALYSIS_INSTRUCTION = """
            You are the analysis and reporting sub-agent. Responsible for:
            1. Verifying detail data reasonability (check-detail-data)
            2. Aggregating by simplified scenario and analyzing volatility (generate-report)
            3. Outputting the final analysis conclusion

            Processing result: {processing_result}
            Execute the analysis steps and output the final report.
            """;

    @Bean
    public SkillRegistry financeSkillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    @Bean
    public SkillsAgentHook financeSkillsAgentHook(SkillRegistry financeSkillRegistry, FinanceTools financeTools) {
        return SkillsAgentHook.builder()
                .skillRegistry(financeSkillRegistry)
                .groupedTools(Map.of(
                        "read-system-data", List.of(financeTools),
                        "read-account-detail-data", List.of(financeTools),
                        "mark-level", List.of(financeTools),
                        "mark-detail-level", List.of(financeTools),
                        "mark-ic-level", List.of(financeTools),
                        "filter-detail-data", List.of(financeTools),
                        "check-detail-data", List.of(financeTools),
                        "generate-report", List.of(financeTools)
                ))
                .build();
    }

    @Bean
    public ReactAgent financeMainAgent(ChatModel chatModel, SkillsAgentHook financeSkillsAgentHook) {
        return ReactAgent.builder()
                .name("finance_main_agent")
                .model(chatModel)
                .instruction(FINANCE_MAIN_INSTRUCTION)
                .hooks(List.of(financeSkillsAgentHook))
                .outputKey("report_data_result")
                .saver(new MemorySaver())
                .maxIterations(20)
                .enableLogging(true)
                .build();
    }

    @Bean
    public ReactAgent dataRetrievalAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("data_retrieval_agent")
                .model(chatModel)
                .description("Retrieve report data and detail data")
                .instruction(DATA_RETRIEVAL_INSTRUCTION)
                .outputKey("detail_data_result")
                .build();
    }

    @Bean
    public ReactAgent dataProcessingAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("data_processing_agent")
                .model(chatModel)
                .description("Process data: equity marking, tier association, IC update, filtering")
                .instruction(DATA_PROCESSING_INSTRUCTION)
                .outputKey("processing_result")
                .build();
    }

    @Bean
    public ReactAgent analysisReportAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("analysis_report_agent")
                .model(chatModel)
                .description("Verify data and generate analysis report")
                .instruction(ANALYSIS_INSTRUCTION)
                .outputKey("analysis_result")
                .build();
    }

    @Bean
    public SequentialAgent financeWorkflowAgent(
            ReactAgent financeMainAgent,
            ReactAgent dataRetrievalAgent,
            ReactAgent dataProcessingAgent,
            ReactAgent analysisReportAgent) {
        return SequentialAgent.builder()
                .name("finance_workflow")
                .description("Complete financial report volatility analysis workflow: Data Retrieval -> Data Processing -> Analysis Report")
                .subAgents(List.of(financeMainAgent, dataRetrievalAgent, dataProcessingAgent, analysisReportAgent))
                .build();
    }
}
