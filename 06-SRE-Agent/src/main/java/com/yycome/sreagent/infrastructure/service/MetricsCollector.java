package com.yycome.sreagent.infrastructure.service;

import com.yycome.sreagent.infrastructure.service.model.PerformanceReport;
import com.yycome.sreagent.infrastructure.service.model.ToolMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 性能指标收集器
 * 收集和统计工具调用的性能指标
 */
@Slf4j
@Service
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    private final Map<String, ToolMetrics> toolMetricsMap = new ConcurrentHashMap<>();

    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[METRICS] 性能指标收集器初始化完成");
    }

    public void recordToolCall(String toolName, long duration, boolean success) {
        ToolMetrics metrics = toolMetricsMap.computeIfAbsent(toolName, k -> new ToolMetrics(k));
        metrics.recordCall(duration, success);

        Timer.builder("sre.tool.duration")
                .tag("tool", toolName)
                .tag("success", String.valueOf(success))
                .description("Tool execution duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(duration));

        log.debug("[METRICS] 记录工具调用: tool={}, duration={}ms, success={}", toolName, duration, success);
    }

    public ToolMetrics getToolMetrics(String toolName) {
        return toolMetricsMap.get(toolName);
    }

    public PerformanceReport getReport() {
        PerformanceReport report = new PerformanceReport();
        toolMetricsMap.forEach(report::addToolMetrics);
        log.info("[METRICS] 生成性能报告，工具数量: {}", toolMetricsMap.size());
        return report;
    }

    public String getReportSummary() {
        return getReport().getSummary();
    }

    public void clear() {
        toolMetricsMap.clear();
        log.info("[METRICS] 清空所有性能指标");
    }

    public Set<String> getToolNames() {
        return toolMetricsMap.keySet();
    }
}
