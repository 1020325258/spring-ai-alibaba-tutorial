package com.yycome.sremate.infrastructure.service;

import com.yycome.sremate.infrastructure.service.model.TraceSession;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪服务
 * 负责追踪工具调用链路和会话管理
 */
@Slf4j
@Service
public class TracingService {

    private final Map<String, TraceSession> sessions = new ConcurrentHashMap<>();
    private final ThreadLocal<TracingContext> currentContext = new ThreadLocal<>();

    public TraceSession startSession(String userId, String query) {
        String sessionId = UUID.randomUUID().toString();
        TraceSession session = new TraceSession(sessionId, userId, query);
        sessions.put(sessionId, session);
        log.info("[TRACE] 开始会话: sessionId={}, userId={}, query={}", sessionId, userId, query);
        return session;
    }

    public TracingContext startToolCall(String toolName, Map<String, Object> params) {
        TracingContext context = new TracingContext();
        context.setTraceId(UUID.randomUUID().toString());
        context.setToolName(toolName);
        context.setParams(params);
        context.setStartTime(System.currentTimeMillis());
        currentContext.set(context);
        log.info("[TRACE] 开始工具调用: tool={}, traceId={}, params={}", toolName, context.getTraceId(), params);
        return context;
    }

    public void endToolCall(TracingContext context, Object result) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(true);
        context.setResult(result);
        log.info("[TRACE] 工具调用成功: tool={}, traceId={}, duration={}ms",
                context.getToolName(), context.getTraceId(), context.getDuration());
        currentContext.remove();
    }

    public void failToolCall(TracingContext context, Throwable error) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(false);
        context.setErrorMessage(error.getMessage());
        log.error("[TRACE] 工具调用失败: tool={}, traceId={}, duration={}ms, error={}",
                context.getToolName(), context.getTraceId(), context.getDuration(), error.getMessage());
        currentContext.remove();
    }

    public void endSession(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session != null) {
            session.end();
            log.info("[TRACE] 结束会话: sessionId={}, 工具调用次数={}", sessionId, session.getToolCalls().size());
        }
    }

    public TraceSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public TracingContext getCurrentContext() {
        return currentContext.get();
    }

    public void cleanupExpiredSessions() {
        if (sessions.size() > 1000) {
            log.info("[TRACE] 清理过期会话，当前会话数: {}", sessions.size());
            sessions.clear();
        }
    }

    public String visualizeTraceChain(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session == null) return "会话不存在";

        StringBuilder sb = new StringBuilder();
        sb.append("用户问题: ").append(session.getQuery()).append("\n");
        sb.append("  ↓\n");

        for (TracingContext context : session.getToolCalls()) {
            String status = context.isSuccess() ? "✓" : "✗";
            sb.append(String.format("[%s] %s (耗时: %dms)\n",
                    status, context.getToolName(), context.getDuration()));
            if (!context.isSuccess()) {
                sb.append("  错误: ").append(context.getErrorMessage()).append("\n");
            }
        }

        return sb.toString();
    }
}
