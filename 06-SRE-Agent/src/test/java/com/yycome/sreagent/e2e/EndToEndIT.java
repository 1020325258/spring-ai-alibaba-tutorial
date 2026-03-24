package com.yycome.sreagent.e2e;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.yycome.sreagent.node.InvestigateAgentNode;
import com.yycome.sreagent.node.QueryAgentNode;
import com.yycome.sreagent.node.SupervisorNode;
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
 * 端到端验收测试
 * 验收：8.5 端到端验收
 */
@ExtendWith(MockitoExtension.class)
class EndToEndIT {

    @Mock
    private OverAllState mockState;

    private final SupervisorNode supervisorNode = new SupervisorNode();
    private final QueryAgentNode queryAgentNode = new QueryAgentNode();
    private final InvestigateAgentNode investigateAgentNode = new InvestigateAgentNode();

    @Test
    @DisplayName("完整查询流程：用户 -> Supervisor -> Query -> 结果")
    void completeQueryFlow() throws Exception {
        // Step 1: 用户输入
        String userQuery = "查询合同C123的信息";

        // Step 2: Supervisor 识别意图
        when(mockState.value("query", String.class)).thenReturn(Optional.of(userQuery));
        Map<String, Object> supervisorResult = supervisorNode.apply(mockState);

        assertThat(supervisorResult.get("agent_type")).isEqualTo("query");

        // Step 3: Query Agent 执行查询
        Map<String, Object> queryResult = queryAgentNode.apply(mockState);

        assertThat(queryResult).containsKey("query_result");
        assertThat(queryResult.get("query_result")).isNotNull();
    }

    @Test
    @DisplayName("完整排查流程：用户 -> Supervisor -> Investigate -> Skill -> 结论")
    void completeInvestigateFlow() throws Exception {
        // Step 1: 用户输入
        String userQuery = "排查合同发起时缺少个性化报价的原因";

        // Step 2: Supervisor 识别意图
        when(mockState.value("query", String.class)).thenReturn(Optional.of(userQuery));
        Map<String, Object> supervisorResult = supervisorNode.apply(mockState);

        assertThat(supervisorResult.get("agent_type")).isEqualTo("investigate");

        // Step 3: Investigate Agent 执行排查
        Map<String, Object> investigateResult = investigateAgentNode.apply(mockState);

        assertThat(investigateResult).containsKey("investigation_result");
        assertThat(investigateResult.get("investigation_result")).isNotNull();
    }
}
