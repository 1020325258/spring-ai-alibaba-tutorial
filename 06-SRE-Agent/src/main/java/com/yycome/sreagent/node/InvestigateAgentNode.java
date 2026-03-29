package com.yycome.sreagent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Investigate Agent Node - 排查 Agent
 * 负责调用 Skill 获取排查 SOP 并执行
 */
public class InvestigateAgentNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(InvestigateAgentNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 获取用户输入
        String query = state.value("query", String.class).orElse("");

        logger.info("Investigate Agent Node 接收排查请求: {}", query);

        // TODO: 调用 read_skill 加载 Skill
        // TODO: 按 Skill 指令执行排查步骤

        String investigationResult = "排查结论（待实现）";

        logger.info("Investigate Agent Node 返回结论: {}", investigationResult);

        result.put("investigation_result", investigationResult);

        return result;
    }
}
