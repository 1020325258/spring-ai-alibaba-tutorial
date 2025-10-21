package com.alibaba.yycome.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.yycome.enums.StateKeyEnum;
import com.alibaba.yycome.node.PlanAcceptNode;
import com.alibaba.yycome.node.PlannerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class GraphConfiguration {

    private Logger logger = LoggerFactory.getLogger(GraphConfiguration.class);

    @Autowired
    private ChatClient plannerAgent;

    @Autowired
    private ChatClient planAcceptAgent;

    @Bean
    public StateGraph analysisGraph() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 用户输入
            keyStrategyHashMap.put(StateKeyEnum.QUERY.getKey(), new ReplaceStrategy());
            keyStrategyHashMap.put(StateKeyEnum.AUTO_ACCEPT_PLAN.getKey(), new ReplaceStrategy());
            keyStrategyHashMap.put(StateKeyEnum.FINAL_ANSWER.getKey(), new ReplaceStrategy());
            keyStrategyHashMap.put(StateKeyEnum.PLANNER_CONTENT.getKey(), new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("Evaluation Graph", keyStrategyFactory)
                .addNode("planner", AsyncNodeAction.node_async(new PlannerNode(plannerAgent)))
                .addNode("plan_accept", AsyncNodeAction.node_async(new PlanAcceptNode(planAcceptAgent)))
                .addEdge(StateGraph.START, "planner")
                .addEdge("planner", "plan_accept")
                .addEdge("plan_accept", StateGraph.END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }


}
