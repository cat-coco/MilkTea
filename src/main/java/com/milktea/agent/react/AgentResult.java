package com.milktea.agent.react;

import java.util.List;

/**
 * Represents the final result of a ReAct agent execution,
 * including the answer and the full reasoning trace.
 */
public record AgentResult(
        String finalAnswer,
        List<AgentStep> steps,
        int iterations
) {}
