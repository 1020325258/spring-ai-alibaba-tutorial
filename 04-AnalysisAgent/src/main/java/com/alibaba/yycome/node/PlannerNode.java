package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.yycome.entity.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlannerNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(PlannerNode.class);
    
    private ChatClient plannerAgent;

    public PlannerNode(ChatClient chatClient) {
        this.plannerAgent = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> resultMap = new HashMap<>();
        String query = state.value("query", "");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(query));
        String content = plannerAgent.prompt().messages(messages).call().content();
        logger.info("planner output:" + content);

        BeanOutputConverter<Plan> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<Plan>() {});
        try {
            Plan plan = converter.convert(content);
        } catch (Exception e) {
            logger.info("planner convert error", e);
        }
        resultMap.put("planner_content", content);
        return resultMap;
    }
}
