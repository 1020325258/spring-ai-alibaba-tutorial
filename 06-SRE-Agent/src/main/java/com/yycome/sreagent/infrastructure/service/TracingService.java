package com.yycome.sreagent.infrastructure.service;

import com.yycome.sreagent.infrastructure.service.model.TracingContext;
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
        // 计算结果记录数
        context.setRecordCount(computeRecordCount(result));
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
     * 获取最近的工具调用记录（返回副本，防止并发修改）
     */
    public ConcurrentLinkedDeque<TracingContext> getRecentTraces() {
        return new ConcurrentLinkedDeque<>(recentTraces);
    }

    /**
     * 获取原始引用（用于内部遍历，由调用方保证不修改）
     */
    ConcurrentLinkedDeque<TracingContext> getRecentTracesRaw() {
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

    /**
     * 计算结果记录数
     */
    private Integer computeRecordCount(Object result) {
        if (result == null) {
            return 0;
        }
        try {
            // 如果是字符串，尝试解析为 JSON
            if (result instanceof String str) {
                if (str.startsWith("{")) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<?, ?> map = mapper.readValue(str, Map.class);
                    return extractRecordCount(map);
                }
            }
            // 如果是 Map，直接提取
            if (result instanceof Map<?, ?> map) {
                return extractRecordCount(map);
            }
        } catch (Exception e) {
            log.debug("计算记录数失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从 Map 中提取记录数
     */
    private Integer extractRecordCount(Map<?, ?> map) {
        // 优先查找 records 字段
        Object records = map.get("records");
        if (records instanceof java.util.List<?> list) {
            return list.size();
        }
        // 查找 total 或 count 字段
        Object total = map.get("total");
        if (total instanceof Integer) {
            return (Integer) total;
        }
        Object count = map.get("count");
        if (count instanceof Integer) {
            return (Integer) count;
        }
        return null;
    }
}
