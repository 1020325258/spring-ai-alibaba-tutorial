package com.yycome.sremate.infrastructure.service;

import com.yycome.sremate.infrastructure.service.model.TracingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 追踪服务
 * 负责追踪工具调用链路（简化版，无会话管理）
 */
@Slf4j
@Service
public class TracingService {

    /** 保留最近 N 条工具调用记录 */
    private static final int MAX_TRACES = 100;
    private final ConcurrentLinkedDeque<TracingContext> recentTraces = new ConcurrentLinkedDeque<>();
    private final AtomicInteger traceCount = new AtomicInteger(0);

    public TracingContext startToolCall(String toolName, Map<String, Object> params) {
        TracingContext context = new TracingContext();
        context.setTraceId(UUID.randomUUID().toString());
        context.setToolName(toolName);
        context.setParams(params);
        context.setStartTime(System.currentTimeMillis());
        return context;
    }

    public void endToolCall(TracingContext context, Object result) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(true);
        context.setResult(result);
        addTrace(context);
    }

    public void failToolCall(TracingContext context, Throwable error) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(false);
        context.setErrorMessage(error.getMessage());
        addTrace(context);
    }

    private void addTrace(TracingContext context) {
        recentTraces.addFirst(context);
        // 保持队列大小
        while (recentTraces.size() > MAX_TRACES) {
            recentTraces.removeLast();
        }
        traceCount.incrementAndGet();
    }

    /**
     * 获取最近的工具调用记录
     */
    public ConcurrentLinkedDeque<TracingContext> getRecentTraces() {
        return recentTraces;
    }

    /**
     * 获取追踪链可视化字符串（供 trace 命令使用）
     */
    public String visualizeRecentTraces(int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("最近的工具调用记录:\n");

        int count = 0;
        for (TracingContext context : recentTraces) {
            if (count >= limit) break;
            String status = context.isSuccess() ? "✓" : "✗";
            sb.append(String.format("[%s] %s (耗时: %dms)\n",
                    status, context.getToolName(), context.getDuration()));
            if (!context.isSuccess()) {
                sb.append("  错误: ").append(context.getErrorMessage()).append("\n");
            }
            count++;
        }

        if (recentTraces.isEmpty()) {
            sb.append("暂无追踪数据\n");
        }

        return sb.toString();
    }

    /**
     * 获取总调用次数
     */
    public int getTraceCount() {
        return traceCount.get();
    }
}
