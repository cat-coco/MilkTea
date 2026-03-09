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
            你是一个财报波动合理性分析的AI智能助手。你的职责包括：
            1. 帮助用户分析财务报表项波动的合理性
            2. 提取报表数据、计算波动比例、标注异常波动（超过20%）
            3. 获取异常项的明细数据进行深入分析
            4. 执行数据加工：股权标注、层级关联、IC层级更新、数据过滤
            5. 校验数据合理性
            6. 生成分析报告和结论

            请使用可用的Skills，根据当前工作流步骤决定使用哪个技能。
            需要使用技能时，先通过read_skill工具加载详细指令，然后按指令执行。

            【工作流步骤】
            第一步：获取EFM报表数据并计算波动值（技能：read-system-data）
            第二步：获取波动>20%报表项的明细数据（技能：read-account-detail-data）
            第三步-3.1：获取股权信息并标注8大分层（技能：mark-level）
            第三步-3.2：关联明细数据标注层级（技能：mark-detail-level）
            第三步-3.3：更新SR数据的IC层级（技能：mark-ic-level）
            第三步-3.4：过滤并创建处理后数据（技能：filter-detail-data）
            第四步：校验明细数据合理性（技能：check-detail-data）
            第五步：按简化场景汇聚并分析波动（技能：generate-report）

            【交互规则】
            1. 开始分析前，询问用户要分析哪个实体公司
            2. 执行过程中展示任务规划列表并更新进度
            3. 每个步骤将结果插入在线Excel进行数据可视化
            4. 所有步骤完成后，输出完整的分析结论
            5. 分析过程中如果用户提问，在保持工作流状态的同时回答问题
            6. 全程使用中文回复
            """;

    private static final String DATA_RETRIEVAL_INSTRUCTION = """
            你是数据获取子Agent。负责：
            1. 使用read-system-data技能获取系统报表数据
            2. 计算波动值并标注超过20%的项
            3. 使用read-account-detail-data技能获取异常项的明细数据

            报表数据结果：{report_data_result}
            请执行数据获取步骤并输出结果。全程使用中文。
            """;

    private static final String DATA_PROCESSING_INSTRUCTION = """
            你是数据加工子Agent。负责：
            1. 获取股权信息并标注8大分层（mark-level）
            2. 关联明细数据标注层级（mark-detail-level）
            3. 更新SR数据的IC层级（mark-ic-level）
            4. 过滤并创建处理后数据（filter-detail-data）

            报表数据：{report_data_result}
            明细数据：{detail_data_result}
            请执行数据加工步骤并输出结果。全程使用中文。
            """;

    private static final String ANALYSIS_INSTRUCTION = """
            你是分析报告子Agent。负责：
            1. 校验明细数据合理性（check-detail-data）
            2. 按简化场景汇聚并分析波动（generate-report）
            3. 输出最终分析结论

            加工结果：{processing_result}
            请执行分析步骤并输出最终报告。全程使用中文。
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
                .description("获取报表数据和明细数据")
                .instruction(DATA_RETRIEVAL_INSTRUCTION)
                .outputKey("detail_data_result")
                .build();
    }

    @Bean
    public ReactAgent dataProcessingAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("data_processing_agent")
                .model(chatModel)
                .description("数据加工：股权标注、层级关联、IC更新、过滤")
                .instruction(DATA_PROCESSING_INSTRUCTION)
                .outputKey("processing_result")
                .build();
    }

    @Bean
    public ReactAgent analysisReportAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("analysis_report_agent")
                .model(chatModel)
                .description("校验数据并生成分析报告")
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
                .description("完整的财报波动合理性分析工作流：数据获取 -> 数据加工 -> 分析报告")
                .subAgents(List.of(financeMainAgent, dataRetrievalAgent, dataProcessingAgent, analysisReportAgent))
                .build();
    }
}
