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
 * SupervisorNode 单元测试
 * 验收：8.3 各 Agent 执行验收 - Supervisor 能识别查询/排查意图
 */
@ExtendWith(MockitoExtension.class)
class SupervisorNodeTest {

    private SupervisorNode supervisorNode;

    @Mock
    private OverAllState mockState;

    @BeforeEach
    void setUp() {
        supervisorNode = new SupervisorNode();
    }

    @Test
    @DisplayName("Supervisor 能识别查询意图")
    void shouldIdentifyQueryIntent() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("查询合同C123的信息"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result).containsKey("agent_type");
        assertThat(result.get("agent_type")).isEqualTo("query");
    }

    @Test
    @DisplayName("Supervisor 能识别排查意图")
    void shouldIdentifyInvestigateIntent() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("排查合同发起时缺少个性化报价的原因"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result).containsKey("agent_type");
        assertThat(result.get("agent_type")).isEqualTo("investigate");
    }

    @Test
    @DisplayName("Supervisor 能识别'问题'关键词为排查意图")
    void shouldIdentifyProblemAsInvestigate() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("合同C123有问题"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result.get("agent_type")).isEqualTo("investigate");
    }

    @Test
    @DisplayName("Supervisor 能识别'异常'关键词为排查意图")
    void shouldIdentifyAnomalyAsInvestigate() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("订单数据异常"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result.get("agent_type")).isEqualTo("investigate");
    }

    @Test
    @DisplayName("Supervisor 能识别'为什么'关键词为排查意图")
    void shouldIdentifyWhyAsInvestigate() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("为什么合同没有生效"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result.get("agent_type")).isEqualTo("investigate");
    }

    @Test
    @DisplayName("Supervisor 对默认输入返回查询意图")
    void shouldDefaultToQueryIntent() throws Exception {
        when(mockState.value("query", String.class)).thenReturn(Optional.of("合同C123"));

        Map<String, Object> result = supervisorNode.apply(mockState);

        assertThat(result.get("agent_type")).isEqualTo("query");
    }
}
