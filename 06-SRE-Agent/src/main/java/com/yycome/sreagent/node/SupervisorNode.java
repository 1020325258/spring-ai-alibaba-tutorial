package com.yycome.sreagent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Supervisor Node - 主 Agent
 * 负责理解用户意图，路由到合适的子 Agent
 */
public class SupervisorNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(SupervisorNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 获取用户输入
        String query = state.value("query", String.class).orElse("");

        logger.info("Supervisor Node 接收用户输入: {}", query);

        // 意图识别逻辑
        String agentType = identifyIntent(query);

        logger.info("Supervisor Node 识别意图: {} -> {}", query, agentType);

        result.put("agent_type", agentType);

        return result;
    }

    /**
     * 意图识别
     * 根据用户输入判断是"查询"还是"排查"
     */
    private String identifyIntent(String query) {
        // 排查关键词
        String[] investigationKeywords = {"排查", "问题", "异常", "没有", "缺少", "不对", "为什么", "原因", "诊断"};
        String[] queryKeywords = {"查询", "查一下", "看看", "信息", "数据"};

        for (String keyword : investigationKeywords) {
            if (query.contains(keyword)) {
                return "investigate";
            }
        }

        for (String keyword : queryKeywords) {
            if (query.contains(keyword)) {
                return "query";
            }
        }

        // 默认返回查询
        return "query";
    }
}
