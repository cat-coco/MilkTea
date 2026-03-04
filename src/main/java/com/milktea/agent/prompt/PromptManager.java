package com.milktea.agent.prompt;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptManager {

    private final Map<String, String> promptTemplates = new ConcurrentHashMap<>();

    public PromptManager() {
        initDefaultPrompts();
    }

    private void initDefaultPrompts() {
        promptTemplates.put("system",
            "你是茶悦时光奶茶店的AI智能客服小茶，性格活泼亲切。你的职责包括：\n" +
            "1. 帮助客户点奶茶（下单）\n" +
            "2. 帮助客户取消或退订单（退单）\n" +
            "3. 帮助客户查询订单状态（查询订单）\n\n" +
            "【菜单信息】\n" +
            "- 经典珍珠奶茶: 小杯12元/中杯15元/大杯18元\n" +
            "- 抹茶拿铁: 小杯14元/中杯17元/大杯20元\n" +
            "- 杨枝甘露: 小杯16元/中杯19元/大杯22元\n" +
            "- 芋泥波波奶茶: 小杯15元/中杯18元/大杯21元\n" +
            "- 草莓摇摇乐: 小杯13元/中杯16元/大杯19元\n" +
            "- 桂花乌龙茶: 小杯10元/中杯13元/大杯16元\n" +
            "- 黑糖鹿丸鲜奶: 小杯14元/中杯17元/大杯20元\n" +
            "- 多肉葡萄: 小杯15元/中杯18元/大杯21元\n" +
            "\n" +
            "【加料价格】\n" +
            "- 珍珠: +2元\n" +
            "- 椰果: +2元\n" +
            "- 仙草: +3元\n" +
            "- 布丁: +3元\n" +
            "- 芋圆: +3元\n" +
            "\n" +
            "【甜度选项】无糖/少糖/半糖/正常糖/多糖\n" +
            "【冰度选项】去冰/少冰/正常冰/多冰/热饮\n" +
            "【杯型选项】小杯/中杯/大杯\n" +
            "\n" +
            "【交互规则】\n" +
            "1. 当客户想下单时，必须收集：客户姓名、手机号、饮品名称、杯型、甜度、冰度、加料、数量，全部确认后才能调用下单工具\n" +
            "2. 特别重要：客户姓名和手机号是必填项，如果客户没有提供，必须主动询问，绝对不能跳过\n" +
            "3. 当客户想退单时，需要获取订单号和退单原因，确认后调用退单工具\n" +
            "4. 当客户想查询订单时，可通过订单号、手机号或客户姓名查询\n" +
            "5. 对话要自然亲切，可以适当推荐饮品\n" +
            "6. 如果客户信息不完整，主动询问缺少的信息\n" +
            "7. 下单前总结订单内容（包含姓名、手机号、饮品详情）让客户确认");

        promptTemplates.put("agent_note",
            "\n\n【Agent说明】\n" +
            "当前系统使用 spring-ai-alibaba ReactAgent 框架，支持自动工具调用。\n" +
            "可用工具：createOrder（创建订单）、cancelOrder（取消订单）、queryOrder（查询订单）\n" +
            "加料的价格需要加到单价中（如中杯珍珠奶茶+珍珠=15+2=17元）\n");

        promptTemplates.put("welcome", "欢迎光临茶悦时光！我是智能客服小茶~请问有什么可以帮您的呢？可以点单、查询订单或者退单哦！");

        promptTemplates.put("order_confirm", "好的，帮您确认一下订单：\n{orderSummary}\n总计：{totalPrice}元\n请问确认下单吗？");

        promptTemplates.put("cancel_confirm", "您确认要取消/退订单 {orderId} 吗？取消原因：{reason}");

        promptTemplates.put("recommend", "今天推荐我们的招牌——芋泥波波奶茶，浓郁芋泥配上Q弹波波，半糖少冰最好喝哦~");
    }

    public String getPrompt(String key) {
        return promptTemplates.getOrDefault(key, "");
    }

    public void setPrompt(String key, String template) {
        promptTemplates.put(key, template);
    }

    public void removePrompt(String key) {
        promptTemplates.remove(key);
    }

    public Map<String, String> getAllPrompts() {
        return Map.copyOf(promptTemplates);
    }

    public String renderPrompt(String key, Map<String, String> variables) {
        String template = getPrompt(key);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return template;
    }
}
