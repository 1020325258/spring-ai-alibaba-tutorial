package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

/**
 * 查询 Agent 节点：替代 ReactAgent 的完整 ReAct loop，只做一次 LLM 调用。
 *
 * <p>流程：
 * <ol>
 *   <li>LLM 调用（仅决策工具参数，{@code internalToolExecutionEnabled=false}）</li>
 *   <li>手动执行工具，结果直接写入 state</li>
 * </ol>
 *
 * <p>相比 {@link AgentNode} + ReactAgent，去掉了"工具结果回传 LLM → LLM 逐 token 输出 JSON"这一步，
 * 对大数据量查询（如 S单列表）可消除数十秒的等待。
 */
@Slf4j
public class QueryAgentNode implements NodeAction {

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final List<ToolCallback> toolCallbacks;
    private final ToolCallingChatOptions chatOptions;

    public QueryAgentNode(ChatModel chatModel, String systemPrompt, List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.systemPrompt = systemPrompt;
        this.toolCallbacks = toolCallbacks;
        // 构建一次，复用：绑定工具定义，禁止框架自动执行工具
        this.chatOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("[QueryAgentNode] 收到 input: {}", input);

        // Step 1: LLM 决定调用哪个工具、传什么参数（一次非流式调用）
        ChatResponse response = chatModel.call(new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(input)),
                chatOptions
        ));

        AssistantMessage assistantMessage = response.getResult().getOutput();

        if (!assistantMessage.hasToolCalls()) {
            // LLM 直接回答（无需工具），透传文本
            log.info("[QueryAgentNode] LLM 无工具调用，直接返回文本");
            return Map.of("result", assistantMessage.getText());
        }

        // Step 2: 手动执行工具（queryAgent 每次只调用一个工具）
        AssistantMessage.ToolCall toolCall = assistantMessage.getToolCalls().get(0);
        String toolName = toolCall.name();
        log.info("[QueryAgentNode] 执行工具: {}", toolName);

        ToolCallback callback = toolCallbacks.stream()
                .filter(cb -> cb.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("[QueryAgentNode] 未找到工具: " + toolName));

        String result = callback.call(toolCall.arguments());
        log.info("[QueryAgentNode] 执行完成: {}, result_len={}", toolName, result != null ? result.length() : 0);

        // Step 3: 工具结果直接写入 state，跳过 LLM 二次处理
        return Map.of("result", result != null ? result : "{}");
    }
}
