package com.yycome.sremate.service;

import com.yycome.sremate.infrastructure.service.CacheService;
import com.yycome.sremate.infrastructure.service.MetricsCollector;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.PerformanceReport;
import com.yycome.sremate.infrastructure.service.model.TraceSession;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 基础设施服务测试
 */
class InfrastructureServiceTest {

    private com.yycome.sremate.infrastructure.service.TracingService tracingService;
    private com.yycome.sremate.infrastructure.service.CacheService cacheService;
    private com.yycome.sremate.infrastructure.service.MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        tracingService = new com.yycome.sremate.infrastructure.service.TracingService();
        cacheService = new com.yycome.sremate.infrastructure.service.CacheService();
        metricsCollector = new com.yycome.sremate.infrastructure.service.MetricsCollector(new SimpleMeterRegistry());
    }

    @Test
    void testTracingService() {
        // 测试会话追踪
        TraceSession session = tracingService.startSession("user1", "test query");
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals("user1", session.getUserId());

        // 测试工具调用追踪
        TracingContext context = tracingService.startToolCall("testTool", Map.of("param", "value"));
        assertNotNull(context);
        assertEquals("testTool", context.getToolName());

        // 结束追踪
        tracingService.endToolCall(context, "result");
        assertTrue(context.isSuccess());
        assertTrue(context.getDuration() >= 0);
    }

    @Test
    void testCacheService() {
        // 测试缓存
        String key = "test-key";
        String value = cacheService.getOrCompute(key, () -> "computed-value");

        assertEquals("computed-value", value);

        // 再次获取应该命中缓存
        String cachedValue = cacheService.get(key);
        assertEquals("computed-value", cachedValue);
    }

    @Test
    void testMetricsCollector() {
        // 记录工具调用
        metricsCollector.recordToolCall("tool1", 100, true);
        metricsCollector.recordToolCall("tool1", 200, true);
        metricsCollector.recordToolCall("tool1", 300, false);

        // 获取报告
        PerformanceReport report = metricsCollector.getReport();
        assertNotNull(report);
        assertTrue(report.getToolMetrics().containsKey("tool1"));

        // 验证指标
        var metrics = report.getToolMetrics().get("tool1");
        assertEquals(3, metrics.getTotalCalls().get());
        assertEquals(2, metrics.getSuccessCalls().get());
        assertEquals(1, metrics.getFailedCalls().get());
        assertEquals(200.0, metrics.getAverageDuration(), 0.1);
    }
}
