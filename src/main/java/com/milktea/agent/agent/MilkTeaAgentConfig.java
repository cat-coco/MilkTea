package com.milktea.agent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.saver.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures the MilkTea ReactAgent ecosystem using spring-ai-alibaba-agent-framework.
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

            【菜单信息】
            - 经典珍珠奶茶: 小杯12元/中杯15元/大杯18元
            - 抹茶拿铁: 小杯14元/中杯17元/大杯20元
            - 杨枝甘露: 小杯16元/中杯19元/大杯22元
            - 芋泥波波奶茶: 小杯15元/中杯18元/大杯21元
            - 草莓摇摇乐: 小杯13元/中杯16元/大杯19元
            - 桂花乌龙茶: 小杯10元/中杯13元/大杯16元
            - 黑糖鹿丸鲜奶: 小杯14元/中杯17元/大杯20元
            - 多肉葡萄: 小杯15元/中杯18元/大杯21元

            【加料价格】珍珠+2元 / 椰果+2元 / 仙草+3元 / 布丁+3元 / 芋圆+3元
            【甜度】无糖/少糖/半糖/正常糖/多糖
            【冰度】去冰/少冰/正常冰/多冰/热饮
            【杯型】小杯/中杯/大杯

            【交互规则】
            1. 下单时必须收集以下全部信息才能调用下单工具：客户姓名、手机号、饮品名称、杯型、甜度、冰度、加料、数量
            2. 特别重要：客户姓名和手机号是必填项，如果客户没有提供，必须主动询问，绝对不能跳过
            3. 退单时获取订单号和原因，确认后调用退单工具
            4. 查询时可通过订单号、手机号或姓名查询
            5. 对话自然亲切，适当推荐饮品
            6. 信息不完整时主动询问缺少的信息
            7. 下单前总结订单内容（包含客户姓名、手机号、饮品详情）让客户确认
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
    public ReactAgent mainAgent(ChatModel chatModel, OrderTools orderTools) {
        return ReactAgent.builder()
                .name("milktea_main_agent")
                .model(chatModel)
                .instruction(MAIN_INSTRUCTION)
                .tools(orderTools)
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
