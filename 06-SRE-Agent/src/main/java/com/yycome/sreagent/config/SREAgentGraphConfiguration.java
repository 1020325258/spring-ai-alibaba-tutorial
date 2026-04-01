package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.yycome.sreagent.config.node.AdminNode;
import com.yycome.sreagent.config.node.AgentNode;
import com.yycome.sreagent.config.node.QueryAgentNode;
import com.yycome.sreagent.config.node.RouterDispatcher;
import com.yycome.sreagent.config.node.RouterNode;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import com.yycome.sreagent.infrastructure.service.TracingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;

/**
 * SRE-Agent StateGraph 配置
 * 参考 deepresearch/DeepResearchConfiguration.java 实现
 *
 * 不再继承 Agent，直接构建 StateGraph 作为 @Bean 供 ChatController 使用
 */
@Configuration
public class SREAgentGraphConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SREAgentGraphConfiguration.class);

    @Autowired
    private QueryAgentNode queryAgentNode;

    @Lazy
    @Autowired
    @Qualifier("investigateAgent")
    private ReactAgent investigateAgent;

    @Lazy
    @Autowired
    @Qualifier("adminAgent")
    private ReactAgent adminAgent;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private EntityRegistry entityRegistry;

    @Autowired
    private EnvironmentConfig environmentConfig;

    @Autowired
    private TracingService tracingService;

    @Bean
    public StateGraph stateGraph() throws GraphStateException {
        log.info("构建 SRE-Agent StateGraph...");

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyMap = new HashMap<>();
            keyStrategyMap.put("routingTarget", new ReplaceStrategy());
            keyStrategyMap.put("result", new ReplaceStrategy());
            keyStrategyMap.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            return keyStrategyMap;
        };

        StateGraph graph = new StateGraph("sre-agent", keyStrategyFactory,
                new SpringAIStateSerializer(OverAllState::new));

        graph.addNode("router", node_async(new RouterNode(chatModel)))
             .addNode("queryAgent", node_async(queryAgentNode))
             .addNode("investigateAgent", node_async(new AgentNode(investigateAgent, "investigateAgent", tracingService)))
             .addNode("admin", node_async(new AdminNode(environmentConfig, adminAgent, tracingService, chatModel, skillRegistry, entityRegistry)));

        graph.addEdge(START, "router")
             .addConditionalEdges("router", edge_async(new RouterDispatcher()),
                     Map.of("queryAgent", "queryAgent", "investigateAgent", "investigateAgent", "admin", "admin"))
             .addEdge("queryAgent", END)
             .addEdge("investigateAgent", END)
             .addEdge("admin", END);

        return graph;
    }

    @Bean
    public SREAgentGraphProcess sreAgentGraphProcess(@Qualifier("stateGraph") StateGraph stateGraph) throws com.alibaba.cloud.ai.graph.exception.GraphStateException {
        CompileConfig compileConfig = CompileConfig.builder().build();
        CompiledGraph compiledGraph = stateGraph.compile(compileConfig);
        return new SREAgentGraphProcess(compiledGraph);
    }
}