package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Agent 路由器
 * 根据 Supervisor 识别的意图，决定路由到哪个 Agent
 */
public class AgentRouter implements EdgeAction {

    private final Logger logger = LoggerFactory.getLogger(AgentRouter.class);

    @Override
    public String apply(OverAllState state) {
        // 获取 Supervisor 识别的意图
        String agentType = state.value("agent_type", String.class).orElse("query");

        logger.info("AgentRouter 路由决策: {}", agentType);

        // 根据意图返回目标节点
        if ("investigate".equals(agentType)) {
            return "investigate";
        } else {
            return "query";
        }
    }
}
