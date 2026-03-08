package com.yycome.sremate.aspect;

import com.yycome.sremate.infrastructure.service.MetricsCollector;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 可观测性切面
 * 记录工具调用的详细信息，集成追踪和指标收集
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ObservabilityAspect {

    private final TracingService tracingService;
    private final MetricsCollector metricsCollector;

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();

        // 构建参数Map
        Map<String, Object> params = buildParamsMap(args);

        // 开始追踪
        TracingContext tracing = tracingService.startToolCall(toolName, params);

        log.info("[TOOL_CALL] 开始调用工具: {}, 参数: {}", toolName, Arrays.toString(args));

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 结束追踪
            tracingService.endToolCall(tracing, result);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, true);

            log.info("[TOOL_CALL] 工具调用成功: {}, 耗时: {}ms",
                    toolName, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 记录失败
            tracingService.failToolCall(tracing, e);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, false);

            log.error("[TOOL_CALL] 工具调用失败: {}, 耗时: {}ms, 错误: {}",
                    toolName, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * 构建参数Map
     */
    private Map<String, Object> buildParamsMap(Object[] args) {
        Map<String, Object> params = new HashMap<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                params.put("arg" + i, args[i]);
            }
        }
        return params;
    }
}
