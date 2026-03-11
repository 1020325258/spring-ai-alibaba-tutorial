package com.yycome.sremate.infrastructure.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionTemplateTest {

    @Test
    void execute_withSuccess_shouldReturnResult() {
        String result = ToolExecutionTemplate.execute("testTool", () -> "success data");

        assertThat(result).isEqualTo("success data");
    }

    @Test
    void execute_withException_shouldReturnError() {
        String result = ToolExecutionTemplate.execute("testTool", () -> {
            throw new RuntimeException("测试错误");
        });

        assertThat(result).contains("\"error\"");
        assertThat(result).contains("测试错误");
    }

    @Test
    void execute_withNullResult_shouldReturnNull() {
        String result = ToolExecutionTemplate.execute("testTool", () -> null);

        assertThat(result).isNull();
    }
}
