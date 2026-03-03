package com.milktea.agent.skill.impl;

import com.milktea.agent.skill.BaseSkill;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Text summarization skill - summarizes input text into key points.
 */
@Component
public class SummarizeSkill implements BaseSkill {

    private final ChatModel chatModel;

    public SummarizeSkill(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String getId() {
        return "summarize";
    }

    @Override
    public String getName() {
        return "文本总结";
    }

    @Override
    public String getDescription() {
        return "将输入文本总结为简洁的要点，支持自定义总结风格";
    }

    @Override
    public String execute(Map<String, String> params) {
        String text = params.getOrDefault("text", "");
        String style = params.getOrDefault("style", "简洁要点");
        if (text.isBlank()) return "请提供需要总结的文本内容";

        String systemPrompt = "你是一个专业的文本总结助手。请按照「" + style + "」的风格对用户提供的文本进行总结。";
        try {
            return chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage("请总结以下文本：\n" + text)
            ))).getResult().getOutput().getContent();
        } catch (Exception e) {
            return "总结失败: " + e.getMessage();
        }
    }
}
