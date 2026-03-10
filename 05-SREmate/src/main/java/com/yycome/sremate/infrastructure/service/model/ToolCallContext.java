package com.yycome.sremate.infrastructure.service.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 工具调用上下文（ThreadLocal 持有）
 * 用于可观测性日志的上下文传递
 */
@Getter
@Setter
public class ToolCallContext {

    /** 工具名称 */
    private String toolName;

    /** 调用参数（脱敏后） */
    private Map<String, Object> params;

    /** 开始时间戳（毫秒） */
    private long startTime;

    /** 结束时间戳（毫秒） */
    private long endTime;

    /** 是否成功 */
    private boolean success;

    /** 返回结果（JSON字符串前100字符预览） */
    private String resultPreview;

    /** 错误信息 */
    private String errorMessage;

    /** 是否为数据查询类工具 */
    private boolean dataQuery;

    /** 完整结果（用于 DirectOutputHolder） */
    private Object fullResult;

    /**
     * 获取耗时（毫秒）
     */
    public long getDuration() {
        return endTime - startTime;
    }

    /**
     * 生成日志摘要（用于结构化日志）
     */
    public String toLogSummary() {
        return String.format("tool=%s, duration=%dms, success=%s, dataQuery=%s",
                toolName, getDuration(), success, dataQuery);
    }

    // ========== ThreadLocal 支持 ==========

    private static final ThreadLocal<ToolCallContext> CURRENT = new ThreadLocal<>();

    public static ToolCallContext current() {
        return CURRENT.get();
    }

    public static void set(ToolCallContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 创建并绑定新的上下文
     */
    public static ToolCallContext start(String toolName, Map<String, Object> params) {
        ToolCallContext context = new ToolCallContext();
        context.setToolName(toolName);
        context.setParams(params);
        context.setStartTime(System.currentTimeMillis());
        set(context);
        return context;
    }

    /**
     * 标记成功并清理
     */
    public void endSuccess(Object result) {
        this.endTime = System.currentTimeMillis();
        this.success = true;
        this.fullResult = result;
        if (result != null) {
            String str = result.toString();
            this.resultPreview = str.length() > 100 ? str.substring(0, 100) + "..." : str;
        }
    }

    /**
     * 标记失败并清理
     */
    public void endFailure(Throwable error) {
        this.endTime = System.currentTimeMillis();
        this.success = false;
        this.errorMessage = error.getMessage();
    }
}
