package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.yycome.sreagent.infrastructure.service.TracingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 节点：封装 ReactAgent 调用
 * 收集 Agent 的所有输出写入 state["result"]
 *
 * 增强功能：
 * - 接收会话上下文（recentTurns + historySummary）
 * - 接收预提取参数（entity、value、queryScope）
 * - 注入数据时效性引导
 */
@Slf4j
public class AgentNode implements NodeAction {

    private final ReactAgent agent;
    private final String agentName;
    private final TracingService tracingService;

    private static final String TIMELINESS_GUIDANCE = """
            注意：当用户询问"状态"、"现在"、"当前"、"最新"时，
            请调用工具获取最新数据，不要直接使用历史数据作为答案。
            """;

    public AgentNode(ReactAgent agent, String agentName, TracingService tracingService) {
        this.agent = agent;
        this.agentName = agentName;
        this.tracingService = tracingService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        String sessionId = state.value("sessionId", "");

        log.info("AgentNode [{}] 收到 input: {}, sessionId: {}", agentName, input, sessionId);

        // 构建增强输入
        String enhancedInput = buildEnhancedInput(state, input);

        StringBuilder resultBuilder = new StringBuilder();

        Flux<Message> messageFlux = agent.streamMessages(enhancedInput);

        messageFlux
                .filter(msg -> msg instanceof AssistantMessage am && !am.getText().isEmpty())
                .doOnNext(msg -> resultBuilder.append(((AssistantMessage) msg).getText()))
                .blockLast();

        String result = resultBuilder.toString();
        log.info("AgentNode [{}] 执行完成, result length: {}", agentName, result.length());

        return Map.of("result", result);
    }

    /**
     * 构建增强输入，包含上下文和参数
     */
    private String buildEnhancedInput(OverAllState state, String input) {
        StringBuilder sb = new StringBuilder();

        // 获取预提取参数
        Map<String, Object> extractedParams = state.value("extractedParams", Map.of());
        if (extractedParams != null && !extractedParams.isEmpty()) {
            sb.append("## 预提取参数\n");
            for (Map.Entry<String, Object> entry : extractedParams.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                    sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }
            sb.append("\n");
        }

        // 注入数据时效性引导
        sb.append(TIMELINESS_GUIDANCE).append("\n\n");

        // 当前问题
        sb.append("## 当前问题\n").append(input);

        return sb.toString();
    }
}
