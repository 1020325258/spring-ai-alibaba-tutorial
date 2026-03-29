package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.yycome.sreagent.config.node.AdminNode;
import com.yycome.sreagent.config.node.AgentNode;
import com.yycome.sreagent.config.node.RouterDispatcher;
import com.yycome.sreagent.config.node.RouterNode;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * 用 StateGraph 重写 SRE-Agent，复用 ReactAgent 作为子图。
 * 解决 LlmRoutingAgent + ReactAgent 两层嵌套导致的重复输出问题。
 *
 * extends Agent（抽象类），通过 initGraph() 构建图结构，
 * 供 SREAgentLoader 向 Spring AI Alibaba Studio 注册。
 *
 * 各节点实现见 {@code config.node} 包：RouterNode、AgentNode、AdminNode、RouterDispatcher。
 */
@Component
public class SREAgentGraph extends Agent {

    private static final Logger log = LoggerFactory.getLogger(SREAgentGraph.class);

    @Lazy
    @Autowired
    @Qualifier("queryAgent")
    private ReactAgent queryAgent;

    @Lazy
    @Autowired
    @Qualifier("investigateAgent")
    private ReactAgent investigateAgent;

    private final ChatModel chatModel;
    private final EnvironmentConfig environmentConfig;

    public SREAgentGraph(ChatModel chatModel, EnvironmentConfig environmentConfig) {
        super("sre-agent", "SRE-Agent: 数据查询和问题排查");
        this.chatModel = chatModel;
        this.environmentConfig = environmentConfig;
    }

    /**
     * 重写 streamMessages：调用 compiledGraph.stream()，按节点名从 state 提取输出转为 Message。
     * 参考 GraphProcessor 模式（04-AnalysisAgent），对 Flux&lt;NodeOutput&gt; 按节点逐个处理：
     * <ul>
     *   <li>router 节点 → 输出路由决策标签（读 state["routingTarget"]）</li>
     *   <li>queryAgent / investigateAgent / admin 节点 → 输出结果（读 state["result"]）</li>
     * </ul>
     */
    @Override
    public Flux<Message> streamMessages(String input) throws GraphRunnerException {
        Map<String, Object> inputs = Map.of(OverAllState.DEFAULT_INPUT_KEY, input);
        return Flux.defer(() -> {
            try {
                log.info("streamMessages 开始执行，input={}", input);
                return getAndCompileGraph()
                        .stream(inputs, RunnableConfig.builder().build())
                        .doOnSubscribe(s -> log.info("streamMessages 图已订阅"))
                        .doOnNext(no -> log.info("streamMessages 收到节点: {}", no.node()))
                        .doOnError(e -> log.error("streamMessages 图执行异常（异步）", e))
                        .doOnComplete(() -> log.info("streamMessages 图执行完成"))
                        .flatMap(no -> {
                            String nodeName = no.node();
                            if (StateGraph.START.equals(nodeName) || StateGraph.END.equals(nodeName)) {
                                return Flux.<Message>empty();
                            }
                            if ("router".equals(nodeName)) {
                                String target = no.state().value("routingTarget", "investigateAgent");
                                log.info("streamMessages router → {}", target);
                                return Flux.just((Message) new AssistantMessage(
                                        "> **[路由器]** 路由至 **" + target + "**\n\n"));
                            }
                            if ("queryAgent".equals(nodeName) || "investigateAgent".equals(nodeName)
                                    || "admin".equals(nodeName)) {
                                String result = no.state().value("result", "");
                                log.info("streamMessages {} 结果长度: {}", nodeName, result.length());
                                String normalized = normalizeAndPrettifyJson(result);
                                return Flux.just((Message) new AssistantMessage(normalized));
                            }
                            log.warn("streamMessages 未知节点: {}", nodeName);
                            return Flux.<Message>empty();
                        });
            } catch (Exception e) {
                log.error("streamMessages 图执行异常（同步）", e);
                return Flux.error(e);
            }
        });
    }

    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper();

    /**
     * 规范化查询 Agent 的输出：
     * 1. 确保 ```json 后有换行（修复 react-markdown streaming undefined 问题）
     * 2. 对代码块内的 JSON 做 pretty-print（缩进格式化，便于阅读）
     */
    private String normalizeAndPrettifyJson(String text) {
        int start = text.indexOf("```json");
        if (start < 0) return text;

        int contentStart = start + "```json".length();
        // 跳过可能已有的换行
        if (contentStart < text.length() && text.charAt(contentStart) == '\n') {
            contentStart++;
        }
        int end = text.lastIndexOf("```");
        if (end <= start) return text;

        String jsonContent = text.substring(contentStart, end).trim();
        String pretty;
        try {
            Object obj = PRETTY_MAPPER.readValue(jsonContent, Object.class);
            pretty = PRETTY_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            pretty = jsonContent; // 非合法 JSON 时保持原样
        }

        return text.substring(0, start) + "```json\n" + pretty + "\n```" + text.substring(end + 3);
    }

    /**
     * 实现 Agent 抽象方法：构建 SRE-Agent StateGraph。
     * 由基类 getAndCompileGraph() 调用并缓存编译结果。
     */
    @Override
    protected StateGraph initGraph() throws GraphStateException {
        log.info("构建 SRE-Agent StateGraph...");

        StateGraph graph = new StateGraph(() -> {
            Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put("routingTarget", new ReplaceStrategy());
            keyStrategyMap.put("result", new ReplaceStrategy());
            keyStrategyMap.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        });

        graph.addNode("router", node_async(new RouterNode(chatModel)))
             .addNode("queryAgent", node_async(new AgentNode(queryAgent)))
             .addNode("investigateAgent", node_async(new AgentNode(investigateAgent)))
             .addNode("admin", node_async(new AdminNode(environmentConfig)));

        graph.addEdge(START, "router")
             .addConditionalEdges("router", edge_async(new RouterDispatcher()),
                     Map.of("queryAgent", "queryAgent", "investigateAgent", "investigateAgent", "admin", "admin"))
             .addEdge("queryAgent", END)
             .addEdge("investigateAgent", END)
             .addEdge("admin", END);

        return graph;
    }

}
