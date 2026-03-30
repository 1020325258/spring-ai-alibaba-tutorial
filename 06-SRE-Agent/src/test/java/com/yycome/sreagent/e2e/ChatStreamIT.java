package com.yycome.sreagent.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.config.SREAgentGraphProcess;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRE-Agent 图执行结果端到端测试
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatStreamIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private SREAgentGraphProcess graphProcess;

    // ===== 4 个测试用例 =====

    @Test
    @DisplayName("排查场景：订单合同弹窗提示请先完成报价")
    void investigate_missing_quote() {
        String response = graphProcess.streamAndCollect("排查825123110000002753订单的合同弹窗提示请先完成报价");
        assertThat(response).isNotEmpty();
        String stripped = stripRouterPrefix(response);
        assertThat(stripped).doesNotStartWith("{");
        assertThat(stripped).doesNotStartWith("[");
    }

    @Test
    @DisplayName("查询场景：合同基本信息")
    void query_contract_info() throws Exception {
        String response = graphProcess.streamAndCollect("查询合同C1767173898135504的基本信息");
        assertThat(response).isNotEmpty();
        String json = extractJsonFromMarkdown(stripRouterPrefix(response));
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.has("queryEntity")).isTrue();
        assertThat(root.has("records")).isTrue();
    }

    @Test
    @DisplayName("查询场景：订单下的签约单据")
    void query_order_signable_docs() throws Exception {
        String response = graphProcess.streamAndCollect("查询825123110000002753下的签约单据");
        assertThat(response).isNotEmpty();
        String json = extractJsonFromMarkdown(stripRouterPrefix(response));
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.has("records")).isTrue();
    }

    @Test
    @DisplayName("查询场景：合同的节点")
    void query_contract_nodes() throws Exception {
        String response = graphProcess.streamAndCollect("查询825123110000002753的合同节点");
        assertThat(response).isNotEmpty();
        String json = extractJsonFromMarkdown(stripRouterPrefix(response));
        JsonNode root = MAPPER.readTree(json);
        assertThat(root.has("records")).isTrue();
    }

    // ===== 工具方法 =====

    private String stripRouterPrefix(String text) {
        if (text == null || text.isEmpty()) return text;
        int idx = text.indexOf("> **[路由器]**");
        if (idx >= 0) {
            return text.substring(idx + 39).trim();
        }
        return text.trim();
    }

    private String extractJsonFromMarkdown(String text) throws java.io.IOException {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(6);
        } else if (trimmed.startsWith("```")) {
            int end = trimmed.indexOf("```", 3);
            if (end >= 0) trimmed = trimmed.substring(end + 3);
        }
        trimmed = trimmed.trim();
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}