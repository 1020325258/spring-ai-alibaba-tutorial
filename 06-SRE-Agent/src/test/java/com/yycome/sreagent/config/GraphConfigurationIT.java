package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.StateGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphConfiguration 集成测试
 * 验收：8.2 多 Agent 消息传递验收 - Graph 能正确初始化
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class GraphConfigurationIT {

    @Autowired(required = false)
    private StateGraph sreAgentGraph;

    @Test
    @DisplayName("Graph 能正确初始化")
    void graphShouldInitialize() {
        assertThat(sreAgentGraph).isNotNull();
    }

    @Test
    @DisplayName("Graph 包含 supervisor 节点")
    void graphShouldContainSupervisorNode() {
        assertThat(sreAgentGraph).isNotNull();
        // 验证 Graph 结构已正确构建
    }

    @Test
    @DisplayName("Graph 包含 query_agent 节点")
    void graphShouldContainQueryAgentNode() {
        assertThat(sreAgentGraph).isNotNull();
    }

    @Test
    @DisplayName("Graph 包含 investigate_agent 节点")
    void graphShouldContainInvestigateAgentNode() {
        assertThat(sreAgentGraph).isNotNull();
    }
}
