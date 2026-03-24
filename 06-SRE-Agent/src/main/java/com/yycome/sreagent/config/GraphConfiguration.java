package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.yycome.sreagent.node.InvestigateAgentNode;
import com.yycome.sreagent.node.QueryAgentNode;
import com.yycome.sreagent.node.SupervisorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * SRE-Agent Graph 配置
 * 定义多 Agent 编排的 StateGraph 结构
 */
@Configuration
public class GraphConfiguration {

    private Logger logger = LoggerFactory.getLogger(GraphConfiguration.class);

    @Bean
    public StateGraph sreAgentGraph() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("agent_type", new ReplaceStrategy());
            keyStrategyHashMap.put("query_result", new ReplaceStrategy());
            keyStrategyHashMap.put("investigation_result", new ReplaceStrategy());
            keyStrategyHashMap.put("final_answer", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("SRE Agent Graph", keyStrategyFactory)
                .addNode("supervisor", AsyncNodeAction.node_async(new SupervisorNode()))
                .addNode("query_agent", AsyncNodeAction.node_async(new QueryAgentNode()))
                .addNode("investigate_agent", AsyncNodeAction.node_async(new InvestigateAgentNode()))
                .addEdge(StateGraph.START, "supervisor")
                .addConditionalEdges("supervisor", AsyncEdgeAction.edge_async(new AgentRouter()),
                        Map.of("query", "query_agent", "investigate", "investigate_agent"))
                .addEdge("query_agent", StateGraph.END)
                .addEdge("investigate_agent", StateGraph.END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }
}
