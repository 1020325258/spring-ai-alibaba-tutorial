package com.yycome.sremate.service;

import com.yycome.sremate.domain.ToolExecutionRequest;
import com.yycome.sremate.domain.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 并行执行器
 * 用于并行执行多个工具调用，提升响应速度
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParallelExecutor {

    private final TracingService tracingService;
    private final MetricsCollector metricsCollector;

    // 用于并行执行的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 并行执行多个工具调用
     *
     * @param requests 工具执行请求列表
     * @return 执行结果列表
     */
    public List<CompletableFuture<Object>> executeParallel(List<ToolExecutionRequest> requests) {
        log.info("[PARALLEL] 开始并行执行 {} 个工具调用", requests.size());

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Object>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() ->
                        executeSingleTool(request), executorService
                ))
                .collect(Collectors.toList());

        // 等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - startTime;
        log.info("[PARALLEL] 并行执行完成，总耗时: {}ms", duration);

        return futures;
    }

    /**
     * 执行单个工具
     * 注：简化实现，实际工具调用由Spring AI框架处理
     *
     * @param request 工具执行请求
     * @return 执行结果
     */
    private Object executeSingleTool(ToolExecutionRequest request) {
        String toolName = request.getToolName();
        Map<String, Object> params = request.getParams();

        // 开始追踪
        TracingContext tracing = tracingService.startToolCall(toolName, params);

        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            // 这里简化实现，实际工具调用由Spring AI Agent框架处理
            // 本方法主要用于演示并行执行和追踪机制
            log.info("[PARALLEL] 执行工具: {} - {}", toolName, params);

            // 模拟执行
            Object result = String.format("Tool %s executed successfully with params: %s",
                toolName, params.toString());

            // 记录成功
            success = true;
            tracingService.endToolCall(tracing, result);

            return result;

        } catch (Exception e) {
            // 记录失败
            tracingService.failToolCall(tracing, e);
            log.error("[PARALLEL] 工具执行失败: tool={}, error={}", toolName, e.getMessage());

            return String.format("Error executing tool %s: %s", toolName, e.getMessage());

        } finally {
            // 记录性能指标
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordToolCall(toolName, duration, success);
        }
    }
}
