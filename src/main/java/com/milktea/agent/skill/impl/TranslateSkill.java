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
 * Text translation skill - translates text between languages.
 */
@Component
public class TranslateSkill implements BaseSkill {

    private final ChatModel chatModel;

    public TranslateSkill(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String getId() {
        return "translate";
    }

    @Override
    public String getName() {
        return "文本翻译";
    }

    @Override
    public String getDescription() {
        return "将文本翻译为目标语言，支持中英日韩等多语言互译";
    }

    @Override
    public String execute(Map<String, String> params) {
        String text = params.getOrDefault("text", "");
        String targetLang = params.getOrDefault("targetLanguage", "英文");
        if (text.isBlank()) return "请提供需要翻译的文本内容";

        String systemPrompt = "你是一个专业的翻译助手。请将用户提供的文本准确翻译为" + targetLang + "。只返回翻译结果，不要附加解释。";
        try {
            return chatModel.call(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(text)
            ))).getResult().getOutput().getContent();
        } catch (Exception e) {
            return "翻译失败: " + e.getMessage();
        }
    }
}
