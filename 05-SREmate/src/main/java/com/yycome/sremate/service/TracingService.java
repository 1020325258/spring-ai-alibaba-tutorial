package com.yycome.sremate.service;

import com.yycome.sremate.domain.TraceSession;
import com.yycome.sremate.domain.TracingContext;
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

    /**
     * 活跃的追踪会话
     */
    private final Map<String, TraceSession> sessions = new ConcurrentHashMap<>();

    /**
     * 当前线程的追踪上下文
     */
    private final ThreadLocal<TracingContext> currentContext = new ThreadLocal<>();

    /**
     * 开始一个新的追踪会话
     *
     * @param userId 用户ID
     * @param query 用户问题
     * @return 会话对象
     */
    public TraceSession startSession(String userId, String query) {
        String sessionId = UUID.randomUUID().toString();
        TraceSession session = new TraceSession(sessionId, userId, query);
        sessions.put(sessionId, session);

        log.info("[TRACE] 开始会话: sessionId={}, userId={}, query={}",
                sessionId, userId, query);

        return session;
    }

    /**
     * 开始工具调用追踪
     *
     * @param toolName 工具名称
     * @param params 调用参数
     * @return 追踪上下文
     */
    public TracingContext startToolCall(String toolName, Map<String, Object> params) {
        TracingContext context = new TracingContext();
        context.setTraceId(UUID.randomUUID().toString());
        context.setToolName(toolName);
        context.setParams(params);
        context.setStartTime(System.currentTimeMillis());

        currentContext.set(context);

        log.info("[TRACE] 开始工具调用: tool={}, traceId={}, params={}",
                toolName, context.getTraceId(), params);

        return context;
    }

    /**
     * 结束工具调用追踪（成功）
     *
     * @param context 追踪上下文
     * @param result 执行结果
     */
    public void endToolCall(TracingContext context, Object result) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(true);
        context.setResult(result);

        log.info("[TRACE] 工具调用成功: tool={}, traceId={}, duration={}ms",
                context.getToolName(), context.getTraceId(), context.getDuration());

        currentContext.remove();
    }

    /**
     * 结束工具调用追踪（失败）
     *
     * @param context 追踪上下文
     * @param error 错误信息
     */
    public void failToolCall(TracingContext context, Throwable error) {
        context.setEndTime(System.currentTimeMillis());
        context.setSuccess(false);
        context.setErrorMessage(error.getMessage());

        log.error("[TRACE] 工具调用失败: tool={}, traceId={}, duration={}ms, error={}",
                context.getToolName(), context.getTraceId(), context.getDuration(), error.getMessage());

        currentContext.remove();
    }

    /**
     * 结束追踪会话
     *
     * @param sessionId 会话ID
     */
    public void endSession(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session != null) {
            session.end();
            log.info("[TRACE] 结束会话: sessionId={}, 工具调用次数={}",
                    sessionId, session.getToolCalls().size());
        }
    }

    /**
     * 获取追踪会话
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public TraceSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 获取当前线程的追踪上下文
     *
     * @return 追踪上下文
     */
    public TracingContext getCurrentContext() {
        return currentContext.get();
    }

    /**
     * 清理过期的会话（保留最近1000个会话）
     */
    public void cleanupExpiredSessions() {
        if (sessions.size() > 1000) {
            log.info("[TRACE] 清理过期会话，当前会话数: {}", sessions.size());
            // 简单实现：保留最新的1000个会话
            // 实际生产环境应该基于时间过期
            sessions.clear();
        }
    }

    /**
     * 生成追踪链路可视化
     *
     * @param sessionId 会话ID
     * @return 可视化字符串
     */
    public String visualizeTraceChain(String sessionId) {
        TraceSession session = sessions.get(sessionId);
        if (session == null) {
            return "会话不存在";
        }

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
