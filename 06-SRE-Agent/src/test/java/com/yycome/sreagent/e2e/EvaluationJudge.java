package com.yycome.sreagent.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 语义评估器
 * 使用 LLM (Qwen-Turbo) 判断 Agent 输出是否符合预期描述
 */
public class EvaluationJudge {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Judge Prompt 模板
     */
    private static final String JUDGE_PROMPT_TEMPLATE = """
        你是一个输出评估器。判断【实际输出】是否满足【预期描述】的语义要求。

        ## 输入
        - 问题：%s
        - 预期：%s
        - 实际输出：%s

        ## 判断规则
        1. 语义一致即可，不要求措辞完全相同
        2. 实际输出包含预期描述要求的核心信息即为通过
        3. 只有明确缺失预期要求的内容才判定为不通过
        4. 如果预期描述了多个要求，全部满足才通过

        ## 输出格式
        严格输出以下 JSON，不要输出其他内容：
        {"pass": true, "reason": "简要说明判断依据（30字以内）"}
        """;

    private final ChatModel chatModel;

    public EvaluationJudge(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 评估 Agent 输出是否符合预期
     *
     * @param question     用户问题
     * @param actualOutput Agent 实际输出
     * @param expected     期望输出的自然语言描述
     * @return 评估结果
     */
    public JudgeResult evaluate(String question, String actualOutput, String expected) {
        String prompt = String.format(JUDGE_PROMPT_TEMPLATE, question, expected, actualOutput);
        // 使用 ChatClient 进行调用（复用现有 ChatModel）
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        String response = chatClient.prompt(prompt).call().content();
        return parseJudgeResult(response);
    }

    /**
     * 解析 Judge LLM 返回的 JSON
     */
    private JudgeResult parseJudgeResult(String response) {
        try {
            String json = extractJson(response);
            JsonNode node = MAPPER.readTree(json);

            boolean pass = node.get("pass").asBoolean();
            String reason = node.get("reason").asText();

            return new JudgeResult(pass, reason);
        } catch (Exception e) {
            throw new EvaluationException("解析 Judge 响应失败: " + response, e);
        }
    }

    /**
     * 从响应中提取 JSON（处理可能的 markdown 代码块）
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) return text;

        String trimmed = text.trim();
        // 去除 markdown 代码块
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * 评估结果
     */
    public record JudgeResult(boolean pass, String reason) {}

    /**
     * 评估异常
     */
    public static class EvaluationException extends RuntimeException {
        public EvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
