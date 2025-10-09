package com.alibaba.yycome.config;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.yycome.node.EvaluationNode;
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
    private ChatClient evaluationAgent;

    @Bean
    public StateGraph evaluationGraph() throws GraphStateException {

        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            // 用户输入
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("answer", new ReplaceStrategy());
            keyStrategyHashMap.put("evaluation_output", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph("Evaluation Graph", keyStrategyFactory)
                .addNode("evaluation", AsyncNodeAction.node_async(new EvaluationNode(evaluationAgent)))
                .addEdge(StateGraph.START, "evaluation")
                .addEdge("evaluation", StateGraph.END);

        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
        logger.info("\n\n");
        logger.info(graphRepresentation.content());
        logger.info("\n\n");

        return stateGraph;
    }


}
