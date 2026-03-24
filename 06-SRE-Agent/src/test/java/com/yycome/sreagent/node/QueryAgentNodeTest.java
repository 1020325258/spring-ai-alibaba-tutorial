package com.yycome.sreagent.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * QueryAgentNode 单元测试
 * 验收：8.3 各 Agent 执行验收 - Query Agent 能执行查询并返回结果
 */
@ExtendWith(MockitoExtension.class)
class QueryAgentNodeTest {

    private QueryAgentNode queryAgentNode;

    @Mock
    private OverAllState mockState;

    @BeforeEach
    void setUp() {
        queryAgentNode = new QueryAgentNode();
    }

    @Test
    @DisplayName("Query Agent 能接收查询请求")
    void shouldAcceptQueryRequest() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("查询合同C123的信息"));

        Map<String, Object> result = queryAgentNode.apply(mockState);

        assertThat(result).containsKey("query_result");
    }

    @Test
    @DisplayName("Query Agent 返回查询结果")
    void shouldReturnQueryResult() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("订单825123110000002753的合同"));

        Map<String, Object> result = queryAgentNode.apply(mockState);

        assertThat(result.get("query_result")).isNotNull();
        assertThat(result.get("query_result").toString()).isNotEmpty();
    }
}
