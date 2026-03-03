package com.milktea.agent.workflow;

import java.util.List;
import java.util.Map;

/**
 * JSON-serializable workflow definition supporting branching, loops, sequential execution, and timeout.
 */
public record WorkflowDefinition(
        String id,
        String name,
        String description,
        List<WorkflowNode> nodes,
        List<WorkflowEdge> edges,
        Map<String, Object> config
) {
    public record WorkflowNode(
            String id,
            String name,
            String type,        // "skill" | "condition" | "loop" | "agent" | "start" | "end"
            String skillId,
            Map<String, String> params,
            String condition,   // for if-else: SpEL expression or keyword match
            int timeoutSeconds,
            int maxRetries
    ) {}

    public record WorkflowEdge(
            String from,
            String to,
            String label,
            String condition    // optional: condition for this edge (for branching)
    ) {}
}
