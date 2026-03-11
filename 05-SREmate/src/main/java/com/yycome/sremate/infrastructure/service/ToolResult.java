package com.yycome.sremate.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 工具执行结果 - 统一的 JSON 结果格式
 */
public final class ToolResult {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolResult() {}

    /**
     * 成功结果
     */
    public static String success(Object data) {
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return error("序列化失败: " + e.getMessage());
        }
    }

    /**
     * 错误结果
     */
    public static String error(String message) {
        try {
            return MAPPER.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            // 降级处理：ObjectMapper 失败时手动构建
            return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    /**
     * 资源未找到
     */
    public static String notFound(String resource, String identifier) {
        return error("未找到" + resource + ": " + identifier);
    }
}
