package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.yycome.sreagent.infrastructure.service.TracingService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent 节点：封装 ReactAgent 调用
 * 收集 Agent 的所有输出写入 state["result"]
 */
@Slf4j
public class AgentNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(AgentNode.class);

    private final ReactAgent agent;
    private final String agentName;
    private final TracingService tracingService;

    public AgentNode(ReactAgent agent, String agentName, TracingService tracingService) {
        this.agent = agent;
        this.agentName = agentName;
        this.tracingService = tracingService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("AgentNode [{}] 收到 input: {}", agentName, input);

        StringBuilder resultBuilder = new StringBuilder();

        Flux<Message> messageFlux = agent.streamMessages(input);

        messageFlux
                .filter(msg -> msg instanceof AssistantMessage am && !am.getText().isEmpty())
                .doOnNext(msg -> resultBuilder.append(((AssistantMessage) msg).getText()))
                .blockLast();

        String result = resultBuilder.toString();
        log.info("AgentNode [{}] 执行完成, result length: {}", agentName, result.length());

        return Map.of("result", result);
    }
}