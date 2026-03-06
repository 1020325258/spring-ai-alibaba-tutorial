package com.yycome.sremate.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行请求
 * 用于编排层执行工具调用的请求对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionRequest {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 调用参数
     */
    private Map<String, Object> params;

    /**
     * 创建执行请求
     */
    public static ToolExecutionRequest of(String toolName, Map<String, Object> params) {
        return new ToolExecutionRequest(toolName, params);
    }
}
