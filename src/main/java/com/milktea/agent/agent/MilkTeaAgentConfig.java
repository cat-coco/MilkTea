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
 * Configures the MilkTea ReactAgent ecosystem using spring-ai-alibaba-agent-framework.
 * Uses ClasspathSkillRegistry + SkillsAgentHook for progressive disclosure of skills.
 * Creates a SequentialAgent workflow: Order → Coupon Check → Membership Check → Result Display.
 */
@Configuration
public class MilkTeaAgentConfig {

    private static final String MAIN_INSTRUCTION = """
            你是茶悦时光奶茶店的AI智能客服小茶，性格活泼亲切。你的职责包括：
            1. 帮助客户点奶茶（下单）
            2. 帮助客户取消或退订单（退单）
            3. 帮助客户查询订单状态（查询订单）
            4. 帮助客户进行文本总结和翻译

            请根据可用的 Skills 技能列表来判断需要使用哪个技能。
            当你需要使用某个技能时，先通过 read_skill 工具加载该技能的详细指令，然后按照指令执行。

            【交互规则】
            1. 对话自然亲切，适当推荐饮品
            2. 信息不完整时主动询问缺少的信息
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
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry, OrderTools orderTools) {
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .groupedTools(Map.of(
                        "create-order", List.of(orderTools),
                        "cancel-order", List.of(orderTools),
                        "query-order", List.of(orderTools)
                ))
                .build();
    }

    @Bean
    public ReactAgent mainAgent(ChatModel chatModel, SkillsAgentHook skillsAgentHook) {
        return ReactAgent.builder()
                .name("milktea_main_agent")
                .model(chatModel)
                .instruction(MAIN_INSTRUCTION)
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
