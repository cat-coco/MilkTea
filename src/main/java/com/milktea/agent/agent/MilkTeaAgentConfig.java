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

/**
 * Configures the MilkTea ReactAgent ecosystem using spring-ai-alibaba-agent-framework.
 * Uses ClasspathSkillRegistry + SkillsAgentHook for progressive disclosure of skills.
 * Creates a SequentialAgent workflow: Order → Coupon Check → Membership Check → Result Display.
 */
@Configuration
public class MilkTeaAgentConfig {

    private static final String MAIN_INSTRUCTION = """
            你是茶悦时光奶茶店的AI智能客服小茶，性格活泼亲切。你的职责包括：
            1. 帮助客户点奶茶（下单） - 使用 createOrder 工具
            2. 帮助客户取消或退订单（退单） - 使用 cancelOrder 工具
            3. 帮助客户查询订单状态（查询订单） - 使用 queryOrder 工具
            4. 帮助客户进行文本总结和翻译

            【重要：下单流程】
            下单时必须收集以下全部信息才能调用 createOrder 工具：
            - 客户姓名（必填，不能跳过，必须主动询问）
            - 手机号（必填，不能跳过，必须主动询问）
            - 饮品名称、杯型、甜度、冰度、加料、数量
            信息不完整时，必须先主动询问缺少的信息，绝对不能用默认值代替。
            下单前必须总结订单内容让客户确认。

            【交互规则】
            1. 对话自然亲切，适当推荐饮品
            2. 退单时获取订单号和原因，确认后调用 cancelOrder 工具
            3. 查询时可通过订单号、手机号或姓名，调用 queryOrder 工具

            你还有一些 Skills 技能可用（见系统提示中的技能列表）。
            当你需要了解某个技能的详细操作手册（如完整菜单价格表等），
            可通过 read_skill 工具加载该技能的完整说明。
            """;

    private static final String COUPON_INSTRUCTION = """
            你是优惠券检查助手。检查用户是否有可用的优惠券。
            根据订单信息：{order_result}
            判断是否有可用优惠：
            - 满30减5
            - 满50减10
            - 新用户首单8折
            返回优惠信息或"无可用优惠"。
            """;

    private static final String MEMBERSHIP_INSTRUCTION = """
            你是会员积分助手。根据订单信息检查会员积分升级情况。
            订单信息：{order_result}
            优惠信息：{coupon_result}
            规则：每消费1元积1分，满100分升级银卡，满500分升级金卡。
            返回积分变动和等级信息。
            """;

    @Bean
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry) {
        // SkillsAgentHook 提供渐进式披露：
        // 1. 将 skill 列表（名称+描述）注入系统提示
        // 2. 提供 read_skill 工具，模型可按需加载完整 SKILL.md 获取详细操作指令
        // 注意：核心订单工具直接挂载到 agent 上，不放在 groupedTools 中，
        //       确保模型不需要先调用 read_skill 就能执行下单/退单/查询操作。
        //       SKILL.md 提供的是增强操作指令（菜单、流程、校验规则等）。
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
    }

    @Bean
    public ReactAgent mainAgent(ChatModel chatModel, OrderTools orderTools,
                                SkillsAgentHook skillsAgentHook) {
        return ReactAgent.builder()
                .name("milktea_main_agent")
                .model(chatModel)
                .instruction(MAIN_INSTRUCTION)
                // 核心订单工具直接挂载，确保始终可用
                .tools(orderTools)
                // SkillsAgentHook 提供 read_skill 工具 + 技能列表注入系统提示
                .hooks(List.of(skillsAgentHook))
                .outputKey("order_result")
                .saver(new MemorySaver())
                .maxIterations(10)
                .enableLogging(true)
                .build();
    }

    @Bean
    public ReactAgent couponAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("coupon_agent")
                .model(chatModel)
                .description("检查并应用优惠券")
                .instruction(COUPON_INSTRUCTION)
                .outputKey("coupon_result")
                .build();
    }

    @Bean
    public ReactAgent membershipAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("membership_agent")
                .model(chatModel)
                .description("检查会员积分和等级升级")
                .instruction(MEMBERSHIP_INSTRUCTION)
                .outputKey("membership_result")
                .build();
    }

    @Bean
    public SequentialAgent orderWorkflowAgent(
            ReactAgent mainAgent,
            ReactAgent couponAgent,
            ReactAgent membershipAgent) {
        return SequentialAgent.builder()
                .name("order_workflow")
                .description("完整点单工作流：点单 → 优惠检查 → 会员积分 → 结果展示")
                .subAgents(List.of(mainAgent, couponAgent, membershipAgent))
                .build();
    }
}
