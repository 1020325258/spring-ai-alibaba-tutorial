package com.yycome.sremate.aspect;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import com.yycome.sremate.infrastructure.service.MetricsCollector;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.ToolCallContext;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private final DirectOutputHolder directOutputHolder;

    /** 数据查询类工具名称集合 */
    private static final Set<String> DATA_QUERY_TOOLS = Set.of(
            "queryContractData",
            "queryContractsByOrderId",
            "queryContractInstanceId",
            "queryContractFormId",
            "queryContractConfig",
            "querySubOrderInfo",
            "queryBudgetBillList"
    );

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String toolName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();

        // 构建参数Map（脱敏处理）
        Map<String, Object> params = buildParamsMap(args);

        // 判断是否为数据查询类工具
        boolean isDataQuery = DATA_QUERY_TOOLS.contains(toolName);

        // 创建并绑定上下文
        ToolCallContext callContext = ToolCallContext.start(toolName, params);
        callContext.setDataQuery(isDataQuery);

        // 开始追踪（保留原有 TracingService 兼容性）
        TracingContext tracing = tracingService.startToolCall(toolName, params);

        // 结构化日志：开始
        log.info("[TOOL] {} | params={} | type={}",
                toolName, params, isDataQuery ? "DATA_QUERY" : "KNOWLEDGE");

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 结束上下文
            callContext.endSuccess(result);

            // 结束追踪
            tracingService.endToolCall(tracing, result);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, true);

            // 如果是数据查询类工具，将结果写入 DirectOutputHolder
            if (isDataQuery && result instanceof String) {
                directOutputHolder.set((String) result);
            }

            // 结构化日志：成功
            log.info("[TOOL] {} | duration={}ms | success=true | preview={}",
                    toolName, duration, truncatePreview(result));

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 结束上下文
            callContext.endFailure(e);

            // 记录失败
            tracingService.failToolCall(tracing, e);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, false);

            // 结构化日志：失败
            log.error("[TOOL] {} | duration={}ms | success=false | error={}",
                    toolName, duration, e.getMessage());

            throw e;
        } finally {
            // 清理 ThreadLocal
            ToolCallContext.clear();
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

    /**
     * 截断预览字符串
     */
    private String truncatePreview(Object result) {
        if (result == null) return "null";
        String str = result.toString();
        if (str.length() > 100) {
            return str.substring(0, 100) + "...(" + str.length() + "chars)";
        }
        return str;
    }
}
