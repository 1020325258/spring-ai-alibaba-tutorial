package com.yycome.sremate.infrastructure.service.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 追踪会话
 * 记录一次完整的排查会话
 */
@Data
public class TraceSession {

    /** 会话ID */
    private String sessionId;

    /** 用户ID */
    private String userId;

    /** 用户问题 */
    private String query;

    /** 会话开始时间 */
    private LocalDateTime startTime;

    /** 会话结束时间 */
    private LocalDateTime endTime;

    /** 工具调用链路 */
    private List<TracingContext> toolCalls = new ArrayList<>();

    /** 会话状态 */
    private SessionStatus status = SessionStatus.RUNNING;

    public TraceSession(String sessionId, String userId, String query) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.query = query;
        this.startTime = LocalDateTime.now();
    }

    public void addToolCall(TracingContext context) {
        this.toolCalls.add(context);
    }

    public void end() {
        this.endTime = LocalDateTime.now();
        this.status = SessionStatus.COMPLETED;
    }

    public enum SessionStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}
