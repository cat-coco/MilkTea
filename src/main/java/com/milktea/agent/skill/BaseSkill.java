package com.milktea.agent.skill;

import java.util.Map;

/**
 * Base interface for all executable skills.
 */
public interface BaseSkill {

    String getId();

    String getName();

    String getDescription();

    /**
     * Execute the skill with the given parameters.
     *
     * @param params input parameters for the skill
     * @return skill execution result
     */
    String execute(Map<String, String> params);
}
