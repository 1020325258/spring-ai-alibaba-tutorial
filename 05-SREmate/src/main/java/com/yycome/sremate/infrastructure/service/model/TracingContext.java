package com.yycome.sremate.infrastructure.service.model;

import lombok.Data;
import java.util.Map;

/**
 * 追踪上下文
 * 记录单次工具调用的追踪信息
 */
@Data
public class TracingContext {

    /** 追踪ID */
    private String traceId;

    /** 会话ID */
    private String sessionId;

    /** 工具名称 */
    private String toolName;

    /** 调用参数 */
    private Map<String, Object> params;

    /** 开始时间 */
    private long startTime;

    /** 结束时间 */
    private long endTime;

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
    private String errorMessage;

    /** 结果数据 */
    private Object result;

    public long getDuration() {
        if (endTime > 0 && startTime > 0) {
            return endTime - startTime;
        }
        return 0;
    }
}
