package com.yycome.sremate.service;

import com.yycome.sremate.domain.PerformanceReport;
import com.yycome.sremate.domain.ToolMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
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

    /**
     * 记录工具调用指标
     *
     * @param toolName 工具名称
     * @param duration 耗时（毫秒）
     * @param success 是否成功
     */
    public void recordToolCall(String toolName, long duration, boolean success) {
        // 更新内部指标
        ToolMetrics metrics = toolMetricsMap.computeIfAbsent(
                toolName,
                k -> new ToolMetrics(toolName)
        );

        metrics.recordCall(duration, success);

        // 记录到Micrometer
        Timer.builder("sre.tool.duration")
                .tag("tool", toolName)
                .tag("success", String.valueOf(success))
                .description("Tool execution duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(duration));

        log.debug("[METRICS] 记录工具调用: tool={}, duration={}ms, success={}",
                toolName, duration, success);
    }

    /**
     * 获取工具指标
     *
     * @param toolName 工具名称
     * @return 工具指标
     */
    public ToolMetrics getToolMetrics(String toolName) {
        return toolMetricsMap.get(toolName);
    }

    /**
     * 获取性能报告
     *
     * @return 性能报告
     */
    public PerformanceReport getReport() {
        PerformanceReport report = new PerformanceReport();
        toolMetricsMap.forEach((name, metrics) -> {
            report.addToolMetrics(name, metrics);
        });

        log.info("[METRICS] 生成性能报告，工具数量: {}", toolMetricsMap.size());

        return report;
    }

    /**
     * 获取性能报告摘要
     *
     * @return 报告摘要字符串
     */
    public String getReportSummary() {
        return getReport().getSummary();
    }

    /**
     * 清空所有指标
     */
    public void clear() {
        toolMetricsMap.clear();
        log.info("[METRICS] 清空所有性能指标");
    }

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    public java.util.Set<String> getToolNames() {
        return toolMetricsMap.keySet();
    }
}
