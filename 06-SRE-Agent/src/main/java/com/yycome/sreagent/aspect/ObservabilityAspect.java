package com.yycome.sreagent.aspect;

import com.yycome.sreagent.infrastructure.annotation.DataQueryTool;
import com.yycome.sreagent.infrastructure.service.MetricsCollector;
import com.yycome.sreagent.infrastructure.service.ThinkingContextHolder;
import com.yycome.sreagent.infrastructure.service.ThinkingEventPublisher;
import com.yycome.sreagent.infrastructure.service.TracingService;
import com.yycome.sreagent.infrastructure.service.model.ToolCallContext;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 可观测性切面
 * 记录工具调用的详细信息，集成追踪和指标收集
 *
 * 日志层级：
 * - [TOOL] 工具调用层：工具名 + 参数
 * - [AGENT] Agent 状态层：意图识别、Skill 加载、数据查询、结论输出
 * - [LLM] LLM 调用层：请求/响应（可选，详细模式）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ObservabilityAspect {

    private final TracingService tracingService;
    private final MetricsCollector metricsCollector;
    private final ThinkingEventPublisher thinkingEventPublisher;

    /** 是否启用详细 LLM 日志（生产环境建议关闭） */
    private static final boolean DETAILED_LLM_LOG = false;

    @Around("@annotation(org.springframework.ai.tool.annotation.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String toolName = signature.getName();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        long startTime = System.currentTimeMillis();

        // 构建参数Map（使用实际参数名）
        Map<String, Object> params = buildParamsMap(paramNames, args);

        // 通过反射检查方法是否有 @DataQueryTool 注解
        Method method = signature.getMethod();
        boolean isDataQuery = method.isAnnotationPresent(DataQueryTool.class);

        // 创建并绑定上下文
        ToolCallContext callContext = ToolCallContext.start(toolName, params);
        callContext.setDataQuery(isDataQuery);

        // 开始追踪（保留原有 TracingService 兼容性）
        TracingContext tracing = tracingService.startToolCall(toolName, params);

        // 入口日志：工具名 + 参数（工具调用层）
        log.info("[TOOL] {}({})", toolName, buildParamsString(paramNames, args));

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 结束上下文
            callContext.endSuccess(result);

            // 结束追踪
            tracingService.endToolCall(tracing, result);

            // 发布 Thinking 事件
            publishThinkingEvent(tracing);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, true);

            // 成功日志（工具调用层）
            log.info("[TOOL] {} → {}ms, ok", toolName, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            // 结束上下文
            callContext.endFailure(e);

            // 记录失败
            tracingService.failToolCall(tracing, e);

            // 发布 Thinking 事件（失败时也输出）
            publishThinkingEvent(tracing);

            // 记录性能指标
            metricsCollector.recordToolCall(toolName, duration, false);

            // 错误日志（工具调用层）
            log.error("[TOOL] {} → {}ms, error: {}", toolName, duration, e.getMessage());

            throw e;
        } finally {
            // 清理 ThreadLocal
            ToolCallContext.clear();
        }
    }

    /**
     * 构建参数Map（使用实际参数名）
     */
    private Map<String, Object> buildParamsMap(String[] paramNames, Object[] args) {
        Map<String, Object> params = new HashMap<>();
        if (paramNames != null && args != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                params.put(paramNames[i], args[i]);
            }
        }
        return params;
    }

    /**
     * 构建参数字符串（参数名=值格式）
     */
    private String buildParamsString(String[] paramNames, Object[] args) {
        if (paramNames == null || args == null || paramNames.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramNames.length && i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramNames[i]).append("=").append(formatArg(args[i]));
        }
        return sb.toString();
    }

    /**
     * 格式化参数值（截断过长的值）
     */
    private String formatArg(Object arg) {
        if (arg == null) return "null";
        String str = arg.toString();
        if (str.length() > 50) {
            return str.substring(0, 50) + "...";
        }
        return str;
    }

    /**
     * 发布 Thinking 事件到 SSE
     */
    private void publishThinkingEvent(TracingContext tracing) {
        if (tracing == null) {
            return;
        }
        try {
            ThinkingContextHolder.ThinkingContext ctx = ThinkingContextHolder.get();
            if (ctx != null && ctx.getSink() != null) {
                int stepNumber = ctx.nextStep();
                thinkingEventPublisher.publishStepThinking(tracing, ctx.getSink(), stepNumber);
            }
        } catch (Exception e) {
            log.warn("发布 Thinking 事件失败: {}", e.getMessage());
        }
    }
}
