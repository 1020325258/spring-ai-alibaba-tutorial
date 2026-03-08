package com.yycome.sremate.infrastructure.service.model;

import lombok.Data;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 工具性能指标
 * 记录工具调用的统计信息
 */
@Data
public class ToolMetrics {

    /** 工具名称 */
    private String toolName;

    /** 总调用次数 */
    private AtomicInteger totalCalls = new AtomicInteger(0);

    /** 成功调用次数 */
    private AtomicInteger successCalls = new AtomicInteger(0);

    /** 失败调用次数 */
    private AtomicInteger failedCalls = new AtomicInteger(0);

    /** 总耗时（毫秒） */
    private AtomicLong totalDuration = new AtomicLong(0);

    /** 最大耗时（毫秒） */
    private AtomicLong maxDuration = new AtomicLong(0);

    /** 最小耗时（毫秒） */
    private AtomicLong minDuration = new AtomicLong(Long.MAX_VALUE);

    public ToolMetrics(String toolName) {
        this.toolName = toolName;
    }

    public void recordCall(long duration, boolean success) {
        totalCalls.incrementAndGet();
        totalDuration.addAndGet(duration);

        if (success) {
            successCalls.incrementAndGet();
        } else {
            failedCalls.incrementAndGet();
        }

        updateMax(duration);
        updateMin(duration);
    }

    private void updateMax(long duration) {
        long current;
        do {
            current = maxDuration.get();
            if (duration <= current) break;
        } while (!maxDuration.compareAndSet(current, duration));
    }

    private void updateMin(long duration) {
        long current;
        do {
            current = minDuration.get();
            if (duration >= current) break;
        } while (!minDuration.compareAndSet(current, duration));
    }

    public double getAverageDuration() {
        int calls = totalCalls.get();
        if (calls == 0) return 0;
        return (double) totalDuration.get() / calls;
    }

    public double getSuccessRate() {
        int calls = totalCalls.get();
        if (calls == 0) return 0;
        return (double) successCalls.get() / calls * 100;
    }
}
