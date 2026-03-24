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
 * InvestigateAgentNode 单元测试
 * 验收：8.3 各 Agent 执行验收 - Investigate Agent 能加载 Skill 并执行排查步骤
 */
@ExtendWith(MockitoExtension.class)
class InvestigateAgentNodeTest {

    private InvestigateAgentNode investigateAgentNode;

    @Mock
    private OverAllState mockState;

    @BeforeEach
    void setUp() {
        investigateAgentNode = new InvestigateAgentNode();
    }

    @Test
    @DisplayName("Investigate Agent 能接收排查请求")
    void shouldAcceptInvestigateRequest() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("排查合同发起时缺少个性化报价的原因"));

        Map<String, Object> result = investigateAgentNode.apply(mockState);

        assertThat(result).containsKey("investigation_result");
    }

    @Test
    @DisplayName("Investigate Agent 返回排查结论")
    void shouldReturnInvestigationResult() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("排查订单825123110000002753缺少个性化报价"));

        Map<String, Object> result = investigateAgentNode.apply(mockState);

        assertThat(result.get("investigation_result")).isNotNull();
        assertThat(result.get("investigation_result").toString()).isNotEmpty();
    }
}
