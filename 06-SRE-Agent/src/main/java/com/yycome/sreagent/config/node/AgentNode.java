package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Map;

/**
 * Agent 节点：封装 ReactAgent 调用，同步等待结果写入 state["result"]
 */
public class AgentNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(AgentNode.class);

    private final ReactAgent agent;

    public AgentNode(ReactAgent agent) {
        this.agent = agent;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("AgentNode 收到 input: {}", input);

        StringBuilder resultBuilder = new StringBuilder();

        agent.streamMessages(input)
                .filter(msg -> msg instanceof AssistantMessage am && !am.getText().isEmpty())
                .doOnNext(msg -> resultBuilder.append(((AssistantMessage) msg).getText()))
                .blockLast();

        String result = resultBuilder.toString();
        log.info("AgentNode 执行完成, result length: {}", result.length());

        return Map.of("result", result);
    }
}
