package com.yycome.sremate.infrastructure.service.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 性能报告
 * 汇总所有工具的性能指标
 */
@Data
public class PerformanceReport {

    /** 报告生成时间 */
    private long generatedTime = System.currentTimeMillis();

    /** 各工具的性能指标 */
    private Map<String, ToolMetrics> toolMetrics = new HashMap<>();

    public void addToolMetrics(String toolName, ToolMetrics metrics) {
        toolMetrics.put(toolName, metrics);
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 性能统计报告 ===\n");
        sb.append(String.format("生成时间: %tF %<tT%n", generatedTime));
        sb.append("\n");

        if (toolMetrics.isEmpty()) {
            sb.append("暂无数据\n");
            return sb.toString();
        }

        for (Map.Entry<String, ToolMetrics> entry : toolMetrics.entrySet()) {
            String toolName = entry.getKey();
            ToolMetrics metrics = entry.getValue();

            sb.append(String.format("%s:%n", toolName));
            sb.append(String.format("  调用次数: %d%n", metrics.getTotalCalls().get()));
            sb.append(String.format("  平均耗时: %.0fms%n", metrics.getAverageDuration()));
            sb.append(String.format("  最大耗时: %dms%n", metrics.getMaxDuration().get()));
            sb.append(String.format("  最小耗时: %dms%n", metrics.getMinDuration().get() == Long.MAX_VALUE ? 0 : metrics.getMinDuration().get()));
            sb.append(String.format("  成功率: %.1f%%%n", metrics.getSuccessRate()));
            sb.append("\n");
        }

        return sb.toString();
    }
}
