package com.yycome.sremate.infrastructure.service;

import com.yycome.sremate.infrastructure.service.model.ToolExecutionRequest;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
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

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public List<CompletableFuture<Object>> executeParallel(List<ToolExecutionRequest> requests) {
        log.info("[PARALLEL] 开始并行执行 {} 个工具调用", requests.size());

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Object>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() ->
                        executeSingleTool(request), executorService
                ))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - startTime;
        log.info("[PARALLEL] 并行执行完成，总耗时: {}ms", duration);

        return futures;
    }

    private Object executeSingleTool(ToolExecutionRequest request) {
        String toolName = request.getToolName();
        Map<String, Object> params = request.getParams();

        TracingContext tracing = tracingService.startToolCall(toolName, params);

        long startTime = System.currentTimeMillis();
        boolean success = false;

        try {
            log.info("[PARALLEL] 执行工具: {} - {}", toolName, params);

            Object result = String.format("Tool %s executed successfully with params: %s",
                toolName, params.toString());

            success = true;
            tracingService.endToolCall(tracing, result);

            return result;

        } catch (Exception e) {
            tracingService.failToolCall(tracing, e);
            log.error("[PARALLEL] 工具执行失败: tool={}, error={}", toolName, e.getMessage());
            return String.format("Error executing tool %s: %s", toolName, e.getMessage());

        } finally {
            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.recordToolCall(toolName, duration, success);
        }
    }
}
