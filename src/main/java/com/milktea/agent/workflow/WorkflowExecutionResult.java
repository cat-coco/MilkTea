package com.milktea.agent.workflow;

import java.util.List;
import java.util.Map;

/**
 * Result of a workflow execution, including step-by-step details.
 */
public record WorkflowExecutionResult(
        String workflowId,
        String status,  // "completed" | "failed" | "interrupted" | "timeout"
        List<StepResult> steps,
        Map<String, Object> finalState,
        long durationMs
) {
    public record StepResult(
            String nodeId,
            String nodeName,
            String nodeType,
            String status,  // "completed" | "skipped" | "failed" | "timeout"
            String output,
            long durationMs
    ) {}
}
