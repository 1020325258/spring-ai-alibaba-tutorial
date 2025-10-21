package com.alibaba.yycome.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.yycome.entity.Plan;
import com.alibaba.yycome.enums.StateKeyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.Map;

public class PlanAcceptNode implements NodeAction {

    private Logger logger = LoggerFactory.getLogger(PlanAcceptNode.class);

    private ChatClient chatClient;

    public PlanAcceptNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();

        String plannerContent = state.value(StateKeyEnum.PLANNER_CONTENT.getKey(), "");
        BeanOutputConverter<Plan> converter = new BeanOutputConverter<>(new ParameterizedTypeReference<Plan>() {});
        try {
            Plan plan = converter.convert(plannerContent);
            Boolean autoAcceptPlan = state.value(StateKeyEnum.AUTO_ACCEPT_PLAN.getKey(), true);
            if (autoAcceptPlan) {
                // 将计划存入 State
                result.put(StateKeyEnum.PLAN.getKey(), plan);
                logger.info("Plan auto accept: {}", plan);
            } else {
                // todo 增加人员反馈节点
            }
        } catch (Exception e) {
            // todo 给 Planner 重新制定计划
            logger.info("planner convert error", e);
        }
        return result;
    }
}
