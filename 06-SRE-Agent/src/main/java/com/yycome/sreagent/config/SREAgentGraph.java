package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

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
 */
@Component
public class SREAgentGraph extends Agent {

    private static final Logger log = LoggerFactory.getLogger(SREAgentGraph.class);

    static final String ROUTER_PROMPT = """
            你是一个路由器，根据用户问题判断应该使用哪个 Agent 处理。

            回复规则：
            - 用户想查询数据（订单、合同、节点、报价等）→ 只回复 "query"
            - 用户想排查问题（排查工单、诊断异常、弹窗提示等）→ 只回复 "investigate"

            用户问题: %s

            注意：只回复一个单词，不要其他文字。
            """;

    @Lazy
    @Autowired
    @Qualifier("queryAgent")
    private ReactAgent queryAgent;

    @Lazy
    @Autowired
    @Qualifier("investigateAgent")
    private ReactAgent investigateAgent;

    private final ChatModel chatModel;

    public SREAgentGraph(ChatModel chatModel) {
        super("sre-agent", "SRE-Agent: 数据查询和问题排查");
        this.chatModel = chatModel;
    }

    /**
     * 重写 streamMessages：先输出路由决策标签，再委托给对应子 Agent 流式输出。
     * 这样 Studio/SSE 前端可以看到：① 路由器的决策（query/investigate）② 哪个 agent 在处理
     * ③ 该 agent 的完整流式输出（token-by-token）。
     *
     * <p>与 GraphProcessor 模式类似：每个 agent 有自己独立的标签输出，可观测各自所做的动作。
     * 区别在于保留了 token 级别的流式输出（而非等节点完成后一次性输出）。
     */
    @Override
    public Flux<Message> streamMessages(String input) throws GraphRunnerException {
        String routing = determineRouting(input);
        String agentName = "query".equals(routing) ? "queryAgent" : "investigateAgent";
        log.info("streamMessages routing: input='{}', routing='{}', agent='{}'", input, routing, agentName);

        // 路由器决策标签（立即输出）
        AssistantMessage routerMsg = new AssistantMessage(
                "> **[路由器]** → `" + routing + "` 类型，路由至 **" + agentName + "**\n\n");
        // Agent 名称标签
        AssistantMessage agentHeader = new AssistantMessage("**[" + agentName + "]**\n\n");

        if ("query".equals(routing)) {
            // 查询结果：收集所有 token，规范化代码块格式后一次性发送。
            // 避免流式渲染时 ```json 无换行导致 Studio 前端 react-markdown children=undefined → 显示 "undefined"
            Flux<Message> agentStream = queryAgent.streamMessages(input)
                    .filter(msg -> msg instanceof AssistantMessage am && !am.getText().isEmpty())
                    .collectList()
                    .flatMapMany(messages -> {
                        String fullText = messages.stream()
                                .map(m -> ((AssistantMessage) m).getText())
                                .collect(Collectors.joining());
                        // 规范化：确保 ```json 后有换行（LLM 有时漏掉换行导致解析失败）
                        String normalized = fullText.replaceAll("```json([^\\n])", "```json\n$1");
                        return Flux.just((Message) new AssistantMessage(normalized));
                    });
            return Flux.concat(
                    Flux.just((Message) routerMsg, (Message) agentHeader),
                    agentStream
            );
        } else {
            // 排查结论：保持 token 级别流式输出，展示逐步推理过程
            Flux<Message> agentStream = investigateAgent.streamMessages(input);
            return Flux.concat(
                    Flux.just((Message) routerMsg, (Message) agentHeader),
                    agentStream
            );
        }
    }

    private String determineRouting(String input) {
        try {
            String fullPrompt = String.format(ROUTER_PROMPT, input);
            var response = chatModel.call(new Prompt(fullPrompt));
            String result = response.getResult().getOutput().getText().trim().toLowerCase();
            return result.contains("query") ? "query" : "investigate";
        } catch (Exception e) {
            log.warn("路由 LLM 调用失败，降级为 investigate", e);
            return "investigate";
        }
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
            keyStrategyMap.put("routing", new ReplaceStrategy());
            keyStrategyMap.put("result", new ReplaceStrategy());
            keyStrategyMap.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        });

        graph.addNode("router", node_async(new RouterNode(chatModel)))
             .addNode("queryAgent", node_async(new AgentNode(queryAgent)))
             .addNode("investigateAgent", node_async(new AgentNode(investigateAgent)));

        graph.addEdge(START, "router")
             .addConditionalEdges("router", edge_async(new RouterEdge()),
                     Map.of("queryAgent", "queryAgent", "investigateAgent", "investigateAgent"))
             .addEdge("queryAgent", END)
             .addEdge("investigateAgent", END);

        return graph;
    }

    // ==================== 内部类 ====================

    /**
     * 路由节点：LLM 判断用户意图
     */
    private static class RouterNode implements com.alibaba.cloud.ai.graph.action.NodeAction {
        private final ChatModel chatModel;
        private final Logger log = LoggerFactory.getLogger(RouterNode.class);


        public RouterNode(ChatModel chatModel) {
            this.chatModel = chatModel;
        }

        @Override
        public Map<String, Object> apply(OverAllState state) throws Exception {
            String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
            log.info("RouterNode 收到 input: {}", input);

            String fullPrompt = String.format(ROUTER_PROMPT, input);
            var response = chatModel.call(new Prompt(fullPrompt));
            String result = response.getResult().getOutput().getText().trim().toLowerCase();

            String routing = result.contains("query") ? "query" : "investigate";
            log.info("RouterNode routing 结果: {}", routing);

            return Map.of("routing", routing);
        }
    }

    /**
     * Agent 节点：封装 ReactAgent 调用，同步等待结果
     */
    private static class AgentNode implements com.alibaba.cloud.ai.graph.action.NodeAction {
        private final ReactAgent agent;
        private final Logger log = LoggerFactory.getLogger(AgentNode.class);

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

    /**
     * 路由边：根据路由结果选择目标节点
     */
    private static class RouterEdge implements com.alibaba.cloud.ai.graph.action.EdgeAction {
        private final Logger log = LoggerFactory.getLogger(RouterEdge.class);

        @Override
        public String apply(OverAllState state) throws Exception {
            String routing = state.value("routing", "");
            String target = "query".equals(routing) ? "queryAgent" : "investigateAgent";
            log.info("RouterEdge 选择目标: {}", target);
            return target;
        }
    }
}
