package com.milktea.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.milktea.agent.skill.BaseSkill;
import com.milktea.agent.skill.SkillManager;
import com.milktea.agent.workflow.WorkflowDefinition.WorkflowEdge;
import com.milktea.agent.workflow.WorkflowDefinition.WorkflowNode;
import com.milktea.agent.workflow.WorkflowExecutionResult.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Engine for parsing, executing, interrupting, and retrying JSON-defined workflows.
 * Supports branching (if-else), loops (for/while), sequential execution, and timeout control.
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final SkillManager skillManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WorkflowDefinition> workflows = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Active workflow executions for interrupt support
    private final Map<String, Future<?>> activeExecutions = new ConcurrentHashMap<>();

    public WorkflowEngine(SkillManager skillManager) {
        this.skillManager = skillManager;
        loadDefaultWorkflows();
    }

    private void loadDefaultWorkflows() {
        try {
            ClassPathResource resource = new ClassPathResource("workflows/order-workflow.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    WorkflowDefinition def = objectMapper.readValue(is, WorkflowDefinition.class);
                    workflows.put(def.id(), def);
                    log.info("Loaded workflow: {} ({})", def.id(), def.name());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load default workflows: {}", e.getMessage());
        }
    }

    public void registerWorkflow(WorkflowDefinition definition) {
        workflows.put(definition.id(), definition);
    }

    public Optional<WorkflowDefinition> getWorkflow(String workflowId) {
        return Optional.ofNullable(workflows.get(workflowId));
    }

    public List<WorkflowDefinition> getAllWorkflows() {
        return List.copyOf(workflows.values());
    }

    /**
     * Execute a workflow synchronously.
     */
    public WorkflowExecutionResult execute(String workflowId, Map<String, String> initialParams) {
        WorkflowDefinition workflow = workflows.get(workflowId);
        if (workflow == null) {
            return new WorkflowExecutionResult(workflowId, "failed",
                    List.of(), Map.of("error", "Workflow not found"), 0);
        }

        long startTime = System.currentTimeMillis();
        List<StepResult> steps = new ArrayList<>();
        Map<String, Object> state = new HashMap<>(initialParams);

        // Find start node
        String currentNodeId = findStartNodeId(workflow);
        if (currentNodeId == null) {
            return new WorkflowExecutionResult(workflowId, "failed",
                    steps, Map.of("error", "No start node found"), 0);
        }

        int maxSteps = 50; // prevent infinite loops
        int stepCount = 0;

        while (currentNodeId != null && !currentNodeId.equals("end") && stepCount < maxSteps) {
            stepCount++;
            WorkflowNode node = findNode(workflow, currentNodeId);
            if (node == null) break;

            long nodeStart = System.currentTimeMillis();
            StepResult stepResult = executeNode(node, state);
            steps.add(stepResult);

            if ("failed".equals(stepResult.status()) && node.maxRetries() > 0) {
                // Retry logic
                for (int retry = 0; retry < node.maxRetries(); retry++) {
                    log.info("Retrying node {} ({}/{})", node.id(), retry + 1, node.maxRetries());
                    stepResult = executeNode(node, state);
                    steps.add(stepResult);
                    if (!"failed".equals(stepResult.status())) break;
                }
            }

            if ("failed".equals(stepResult.status())) {
                return new WorkflowExecutionResult(workflowId, "failed", steps,
                        state, System.currentTimeMillis() - startTime);
            }

            // Find next node via edges
            currentNodeId = findNextNode(workflow, currentNodeId, state);
        }

        String finalStatus = stepCount >= maxSteps ? "timeout" : "completed";
        return new WorkflowExecutionResult(workflowId, finalStatus, steps,
                state, System.currentTimeMillis() - startTime);
    }

    /**
     * Execute a workflow asynchronously with interrupt support.
     */
    public String executeAsync(String workflowId, Map<String, String> params) {
        String executionId = UUID.randomUUID().toString();
        Future<?> future = executor.submit(() -> execute(workflowId, params));
        activeExecutions.put(executionId, future);
        return executionId;
    }

    /**
     * Interrupt a running workflow execution.
     */
    public boolean interrupt(String executionId) {
        Future<?> future = activeExecutions.get(executionId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            activeExecutions.remove(executionId);
            return true;
        }
        return false;
    }

    private StepResult executeNode(WorkflowNode node, Map<String, Object> state) {
        long start = System.currentTimeMillis();
        try {
            String output;
            switch (node.type()) {
                case "skill" -> {
                    Optional<BaseSkill> skill = skillManager.getSkill(node.skillId());
                    if (skill.isEmpty()) {
                        output = "Skill not found: " + node.skillId();
                        return new StepResult(node.id(), node.name(), node.type(),
                                "failed", output, System.currentTimeMillis() - start);
                    }
                    Map<String, String> params = new HashMap<>();
                    if (node.params() != null) {
                        node.params().forEach((k, v) -> {
                            // Support variable interpolation from state
                            String resolved = resolveVariable(v, state);
                            params.put(k, resolved);
                        });
                    }
                    if (node.timeoutSeconds() > 0) {
                        output = executeWithTimeout(skill.get(), params, node.timeoutSeconds());
                    } else {
                        output = skill.get().execute(params);
                    }
                    state.put(node.id() + "_result", output);
                }
                case "condition" -> {
                    boolean conditionMet = evaluateCondition(node.condition(), state);
                    output = conditionMet ? "true" : "false";
                    state.put(node.id() + "_result", output);
                }
                case "loop" -> {
                    output = "loop_node";
                    state.put(node.id() + "_result", output);
                }
                default -> {
                    output = "executed";
                    state.put(node.id() + "_result", output);
                }
            }
            return new StepResult(node.id(), node.name(), node.type(),
                    "completed", output, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new StepResult(node.id(), node.name(), node.type(),
                    "failed", e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String executeWithTimeout(BaseSkill skill, Map<String, String> params, int timeoutSeconds) {
        try {
            Future<String> future = executor.submit(() -> skill.execute(params));
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "Timeout after " + timeoutSeconds + "s";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private boolean evaluateCondition(String condition, Map<String, Object> state) {
        if (condition == null || condition.isBlank()) return true;
        // Simple keyword-based condition evaluation
        // Format: "stateKey == value" or "stateKey contains value"
        String[] parts = condition.split("\\s+", 3);
        if (parts.length >= 3) {
            Object stateValue = state.get(parts[0]);
            String valueStr = stateValue != null ? stateValue.toString() : "";
            return switch (parts[1]) {
                case "==", "equals" -> valueStr.equals(parts[2]);
                case "!=", "notEquals" -> !valueStr.equals(parts[2]);
                case "contains" -> valueStr.contains(parts[2]);
                case "exists" -> stateValue != null;
                default -> true;
            };
        }
        return true;
    }

    private String resolveVariable(String template, Map<String, Object> state) {
        String result = template;
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String findStartNodeId(WorkflowDefinition workflow) {
        // Look for a node of type "start" or the first node referenced in edges from "start"
        for (WorkflowEdge edge : workflow.edges()) {
            if ("start".equals(edge.from())) return edge.to();
        }
        // Fallback: first node
        return workflow.nodes().isEmpty() ? null : workflow.nodes().getFirst().id();
    }

    private WorkflowNode findNode(WorkflowDefinition workflow, String nodeId) {
        return workflow.nodes().stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst().orElse(null);
    }

    private String findNextNode(WorkflowDefinition workflow, String fromNodeId,
                                 Map<String, Object> state) {
        WorkflowNode currentNode = findNode(workflow, fromNodeId);
        List<WorkflowEdge> outEdges = workflow.edges().stream()
                .filter(e -> e.from().equals(fromNodeId))
                .toList();

        if (outEdges.isEmpty()) return "end";

        // For condition nodes, select edge based on result
        if (currentNode != null && "condition".equals(currentNode.type())) {
            String condResult = String.valueOf(state.getOrDefault(fromNodeId + "_result", "true"));
            for (WorkflowEdge edge : outEdges) {
                if (edge.condition() != null && edge.condition().equals(condResult)) {
                    return edge.to();
                }
            }
        }

        // Default: follow first edge (or edge without condition)
        for (WorkflowEdge edge : outEdges) {
            if (edge.condition() == null || edge.condition().isBlank()) {
                return edge.to();
            }
        }
        return outEdges.getFirst().to();
    }
}
