package com.yycome.sremate.infrastructure.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行模板 - 统一处理计时、日志、异常
 * 让工具类只需关注业务逻辑，遵循 DDD 业务语义编程思想
 */
@Slf4j
public final class ToolExecutionTemplate {

    private ToolExecutionTemplate() {}

    /**
     * 执行工具调用，统一处理日志和错误
     *
     * @param toolName 工具名称，用于日志输出
     * @param action 业务逻辑执行器
     * @return 工具执行结果（JSON 格式），若 action 返回 null 则返回 null
     */
    public static String execute(String toolName, ToolAction action) {
        long start = System.currentTimeMillis();
        try {
            String result = action.execute();
            if (result != null) {
                log.info("[TOOL] {} → {}ms, ok", toolName, System.currentTimeMillis() - start);
            }
            return result;
        } catch (Exception e) {
            log.error("[TOOL] {} → {}ms, error: {}", toolName, System.currentTimeMillis() - start, e.getMessage());
            return ToolResult.error(e.getMessage());
        }
    }

    @FunctionalInterface
    public interface ToolAction {
        String execute() throws Exception;
    }
}
