package com.yycome.sreagent.aspect;

import com.yycome.sreagent.infrastructure.annotation.DataQueryTool;
import com.yycome.sreagent.infrastructure.service.DirectOutputHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ObservabilityAspect 通过 @DataQueryTool 注解识别数据查询工具
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ObservabilityAspectAnnotationTest {

    @Autowired
    private DirectOutputHolder directOutputHolder;

    @Autowired
    private TestTool testTool;

    @BeforeEach
    void setUp() {
        directOutputHolder.startRequest();
    }

    @AfterEach
    void cleanup() {
        directOutputHolder.clear();
    }

    @Test
    void dataQueryToolAnnotation_shouldTriggerDirectOutput() {
        testTool.queryWithDataQueryAnnotation();

        assertThat(directOutputHolder.hasOutput()).isTrue();
        assertThat(directOutputHolder.getAndClearAggregated()).isEqualTo("{\"result\":\"ok\"}");
    }

    @Test
    void noDataQueryToolAnnotation_shouldNotTriggerDirectOutput() {
        testTool.queryWithoutAnnotation();

        assertThat(directOutputHolder.hasOutput()).isFalse();
    }

    /**
     * 测试用工具类配置
     */
    @TestConfiguration
    static class TestToolConfig {

        @Bean
        public TestTool testTool() {
            return new TestTool();
        }
    }

    /**
     * 测试用工具类
     */
    static class TestTool {

        @Tool(description = "带 @DataQueryTool 注解的测试方法")
        @DataQueryTool
        public String queryWithDataQueryAnnotation() {
            return "{\"result\":\"ok\"}";
        }

        @Tool(description = "不带 @DataQueryTool 注解的测试方法")
        public String queryWithoutAnnotation() {
            return "{\"result\":\"ok\"}";
        }
    }
}
