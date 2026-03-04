package com.milktea.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages skill registration, discovery, priority sorting, and conditional triggering.
 */
@Component
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    private final Map<String, SkillDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, BaseSkill> skillInstances = new ConcurrentHashMap<>();

    public SkillManager(List<BaseSkill> skills) {
        // Auto-register all BaseSkill beans
        for (BaseSkill skill : skills) {
            registerSkill(skill);
        }
        initDefaultDefinitions();
    }

    private void initDefaultDefinitions() {
        if (!definitions.containsKey("summarize")) {
            definitions.put("summarize", new SkillDefinition(
                    "summarize", "文本总结",
                    "将输入文本总结为简洁要点",
                    10, true,
                    List.of("总结", "概括", "摘要", "归纳"),
                    "skills/summarize"));
        }
        if (!definitions.containsKey("translate")) {
            definitions.put("translate", new SkillDefinition(
                    "translate", "文本翻译",
                    "将文本翻译为目标语言",
                    10, true,
                    List.of("翻译", "translate", "转换语言"),
                    "skills/translate"));
        }
        // Order skills
        definitions.put("createOrder", new SkillDefinition(
                "createOrder", "创建订单", "创建奶茶订单",
                100, true,
                List.of("点单", "下单", "买", "来一杯", "我想要", "点一杯"),
                null));
        definitions.put("cancelOrder", new SkillDefinition(
                "cancelOrder", "取消订单", "取消或退奶茶订单",
                90, true,
                List.of("取消", "退单", "退款", "不要了"),
                null));
        definitions.put("queryOrder", new SkillDefinition(
                "queryOrder", "查询订单", "查询奶茶订单状态",
                90, true,
                List.of("查询", "查看", "订单状态", "我的订单"),
                null));
    }

    public void registerSkill(BaseSkill skill) {
        skillInstances.put(skill.getId(), skill);
        if (!definitions.containsKey(skill.getId())) {
            definitions.put(skill.getId(), new SkillDefinition(
                    skill.getId(), skill.getName(), skill.getDescription(),
                    10, true, List.of(), null));
        }
        log.info("Registered skill: {} ({})", skill.getId(), skill.getName());
    }

    public void registerDefinition(SkillDefinition definition) {
        definitions.put(definition.id(), definition);
    }

    public Optional<BaseSkill> getSkill(String id) {
        return Optional.ofNullable(skillInstances.get(id));
    }

    public Optional<SkillDefinition> getDefinition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public List<SkillDefinition> getAllDefinitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(SkillDefinition::priority).reversed())
                .collect(Collectors.toList());
    }

    public List<SkillDefinition> getEnabledDefinitions() {
        return getAllDefinitions().stream()
                .filter(SkillDefinition::enabled)
                .collect(Collectors.toList());
    }

    /**
     * Find skills triggered by keyword match in the input, sorted by priority.
     */
    public List<SkillDefinition> findTriggeredSkills(String input) {
        return getEnabledDefinitions().stream()
                .filter(d -> d.matchesKeyword(input))
                .collect(Collectors.toList());
    }

    public void setEnabled(String id, boolean enabled) {
        SkillDefinition existing = definitions.get(id);
        if (existing != null) {
            definitions.put(id, new SkillDefinition(
                    existing.id(), existing.name(), existing.description(),
                    existing.priority(), enabled, existing.triggerKeywords(),
                    existing.skillPath()));
        }
    }

    public void setPriority(String id, int priority) {
        SkillDefinition existing = definitions.get(id);
        if (existing != null) {
            definitions.put(id, new SkillDefinition(
                    existing.id(), existing.name(), existing.description(),
                    priority, existing.enabled(), existing.triggerKeywords(),
                    existing.skillPath()));
        }
    }

    public void unregister(String id) {
        definitions.remove(id);
        skillInstances.remove(id);
    }
}
