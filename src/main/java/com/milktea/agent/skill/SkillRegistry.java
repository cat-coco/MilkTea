package com.milktea.agent.skill;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SkillRegistry {

    private final Map<String, SkillInfo> skills = new LinkedHashMap<>();

    public SkillRegistry() {
        registerDefaultSkills();
    }

    private void registerDefaultSkills() {
        register(new SkillInfo("createOrder", "创建奶茶订单",
                "帮助客户创建新的奶茶订单，需要客户姓名、手机号、饮品信息", true));
        register(new SkillInfo("cancelOrder", "取消/退奶茶订单",
                "帮助客户取消或退已有订单，需要订单号和原因", true));
        register(new SkillInfo("queryOrder", "查询奶茶订单",
                "帮助客户查询订单状态，可通过订单号、手机号或姓名查询", true));
    }

    public void register(SkillInfo skill) {
        skills.put(skill.name(), skill);
    }

    public void unregister(String name) {
        skills.remove(name);
    }

    public void setEnabled(String name, boolean enabled) {
        SkillInfo existing = skills.get(name);
        if (existing != null) {
            skills.put(name, new SkillInfo(existing.name(), existing.displayName(),
                    existing.description(), enabled));
        }
    }

    public List<SkillInfo> getAllSkills() {
        return List.copyOf(skills.values());
    }

    public List<SkillInfo> getEnabledSkills() {
        return skills.values().stream().filter(SkillInfo::enabled).toList();
    }

    public record SkillInfo(String name, String displayName, String description, boolean enabled) {}
}
