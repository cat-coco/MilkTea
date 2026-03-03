package com.milktea.agent.react;

/**
 * Represents a single step in the ReAct agent reasoning chain.
 */
public record AgentStep(
        StepType type,
        String content,
        long timestamp
) {
    public enum StepType {
        THOUGHT("思考"),
        ACTION("行动"),
        OBSERVATION("观察"),
        FINAL_ANSWER("最终回答");

        private final String label;

        StepType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public AgentStep(StepType type, String content) {
        this(type, content, System.currentTimeMillis());
    }
}
