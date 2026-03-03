package com.milktea.agent.skill;

import java.util.List;

/**
 * Metadata for a reusable skill unit.
 */
public record SkillDefinition(
        String id,
        String name,
        String description,
        int priority,
        boolean enabled,
        List<String> triggerKeywords,
        String skillPath
) {
    public boolean matchesKeyword(String input) {
        if (triggerKeywords == null || triggerKeywords.isEmpty()) return false;
        String lower = input.toLowerCase();
        return triggerKeywords.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
    }
}
