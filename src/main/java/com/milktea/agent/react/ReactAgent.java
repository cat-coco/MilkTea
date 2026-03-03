package com.milktea.agent.react;

import com.milktea.agent.react.AgentStep.StepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) Agent implementation.
 * <p>
 * Implements the Thought → Action → Observation loop:
 * 1. The LLM reasons about what to do (Thought)
 * 2. The LLM selects a tool to call (Action + Action Input)
 * 3. The tool is executed and the result is observed (Observation)
 * 4. The loop repeats until a Final Answer is produced
 */
@Component
public class ReactAgent {

    private static final Logger log = LoggerFactory.getLogger(ReactAgent.class);
    private static final int MAX_ITERATIONS = 8;

    // Patterns for parsing LLM output
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
            "(?:Thought|思考)[：:]\\s*(.*?)(?=(?:Action|行动|Final Answer|最终回答)[：:]|$)",
            Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "(?:Action|行动)[：:]\\s*(\\w+)", Pattern.DOTALL);
    private static final Pattern ACTION_INPUT_PATTERN = Pattern.compile(
            "(?:Action Input|行动输入)[：:]\\s*(\\{.*\\})", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
            "(?:Final Answer|最终回答)[：:]\\s*(.*)", Pattern.DOTALL);

    private final ChatModel chatModel;
    private final ToolExecutor toolExecutor;

    public ReactAgent(ChatModel chatModel, ToolExecutor toolExecutor) {
        this.chatModel = chatModel;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Execute the ReAct agent loop.
     *
     * @param systemPrompt the system prompt including ReAct instructions
     * @param history      the conversation history messages
     * @return AgentResult containing the final answer and reasoning steps
     */
    public AgentResult execute(String systemPrompt, List<Message> history) {
        List<AgentStep> steps = new ArrayList<>();
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("ReAct iteration {}/{}", i + 1, MAX_ITERATIONS);

            // Call LLM
            ChatResponse response = chatModel.call(new Prompt(messages));
            String output = response.getResult().getOutput().getContent();
            log.debug("LLM output: {}", output);

            // Try to parse structured ReAct format
            ParsedOutput parsed = parseOutput(output);

            if (parsed.thought != null && !parsed.thought.isBlank()) {
                steps.add(new AgentStep(StepType.THOUGHT, parsed.thought.trim()));
            }

            // Check for Final Answer
            if (parsed.finalAnswer != null) {
                steps.add(new AgentStep(StepType.FINAL_ANSWER, parsed.finalAnswer.trim()));
                return new AgentResult(parsed.finalAnswer.trim(), steps, i + 1);
            }

            // Check for Action
            if (parsed.action != null && parsed.actionInput != null) {
                String actionDesc = parsed.action + ": " + parsed.actionInput;
                steps.add(new AgentStep(StepType.ACTION, actionDesc));

                // Execute the tool
                String observation = toolExecutor.execute(parsed.action, parsed.actionInput);
                steps.add(new AgentStep(StepType.OBSERVATION, observation));

                log.info("Action: {} → Observation: {}", parsed.action, observation);

                // Add assistant output and observation to messages for next iteration
                messages.add(new AssistantMessage(output));
                messages.add(new UserMessage("Observation: " + observation +
                        "\n请根据以上观察结果继续推理。如果已经可以回答用户，请输出 Final Answer。"));
            } else {
                // No structured format detected - treat the whole output as the final answer
                // This handles cases where the LLM responds naturally without ReAct format
                log.info("No ReAct structure detected, treating as direct answer");
                String answer = output.trim();
                steps.add(new AgentStep(StepType.FINAL_ANSWER, answer));
                return new AgentResult(answer, steps, i + 1);
            }
        }

        // Max iterations reached
        log.warn("ReAct agent reached max iterations ({})", MAX_ITERATIONS);
        String fallback = "抱歉，我在处理您的请求时遇到了一些困难。请您再试一次或换个方式描述您的需求。";
        steps.add(new AgentStep(StepType.FINAL_ANSWER, fallback));
        return new AgentResult(fallback, steps, MAX_ITERATIONS);
    }

    private ParsedOutput parseOutput(String output) {
        ParsedOutput parsed = new ParsedOutput();

        // Extract Thought
        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(output);
        if (thoughtMatcher.find()) {
            parsed.thought = thoughtMatcher.group(1).trim();
        }

        // Extract Final Answer
        Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(output);
        if (finalMatcher.find()) {
            parsed.finalAnswer = finalMatcher.group(1).trim();
            return parsed;
        }

        // Extract Action
        Matcher actionMatcher = ACTION_PATTERN.matcher(output);
        if (actionMatcher.find()) {
            parsed.action = actionMatcher.group(1).trim();
        }

        // Extract Action Input (JSON)
        Matcher inputMatcher = ACTION_INPUT_PATTERN.matcher(output);
        if (inputMatcher.find()) {
            parsed.actionInput = inputMatcher.group(1).trim();
        }

        return parsed;
    }

    private static class ParsedOutput {
        String thought;
        String action;
        String actionInput;
        String finalAnswer;
    }
}
