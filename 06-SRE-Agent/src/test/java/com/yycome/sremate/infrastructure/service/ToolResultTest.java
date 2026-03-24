package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void success_shouldReturnJsonWithData() throws Exception {
        Map<String, Object> data = Map.of("contractCode", "C123", "status", 1);
        String result = ToolResult.success(data);

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("contractCode", "C123");
        assertThat(parsed).containsEntry("status", 1);
        assertThat(parsed).doesNotContainKey("error");
    }

    @Test
    void error_shouldReturnJsonWithError() throws Exception {
        String result = ToolResult.error("未找到合同");

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("error", "未找到合同");
        assertThat(parsed).hasSize(1);
    }

    @Test
    void notFound_shouldReturnJsonWithError() throws Exception {
        String result = ToolResult.notFound("合同", "C123");

        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("error", "未找到合同: C123");
    }

    @Test
    void error_withQuotes_shouldEscapeProperly() throws Exception {
        String result = ToolResult.error("消息包含\"引号\"");

        // 验证 JSON 格式正确
        assertThat(result).contains("\"error\"");
        // 使用 JSON 解析器验证转义正确
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertThat(parsed).containsEntry("error", "消息包含\"引号\"");
    }
}
