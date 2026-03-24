package com.yycome.sreagent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Query Agent Node - 查询 Agent
 * 负责调用 ontologyQuery 获取数据
 */
public class QueryAgentNode implements NodeAction {

    private final Logger logger = LoggerFactory.getLogger(QueryAgentNode.class);

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 获取用户输入
        String query = state.value("query", String.class).orElse("");

        logger.info("Query Agent Node 接收查询请求: {}", query);

        // TODO: 调用 ontologyQuery 获取数据
        // 这里需要集成 OntologyQueryTool

        String queryResult = "查询结果数据（待实现）";

        logger.info("Query Agent Node 返回结果: {}", queryResult);

        result.put("query_result", queryResult);

        return result;
    }
}
