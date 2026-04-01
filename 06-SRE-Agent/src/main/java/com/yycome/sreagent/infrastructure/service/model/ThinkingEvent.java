package com.yycome.sreagent.infrastructure.service.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Thinking 事件结构化实体
 * 用于前后端结构化传输thinking数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThinkingEvent {

    /** 节点名称（统一标识） */
    private String nodeName;

    /** 显示标题（中文） */
    private String displayTitle;

    /** 步骤标题 */
    private String stepTitle;

    /** 工具名称 */
    private String toolName;

    /** 调用参数 */
    private Map<String, Object> params;

    /** 参数描述（用于前端展示） */
    private Map<String, String> paramsDescription;

    /** 结果摘要 */
    private String resultSummary;

    /** 结果记录数 */
    private Integer recordCount;

    /** 结果数据（可选，用于需要查看完整数据的场景） */
    private Object resultData;

    /** 耗时（毫秒） */
    private Long duration;

    /** 是否成功 */
    private Boolean success;

    /** 错误信息 */
    private String errorMessage;

    /**
     * 从 TracingContext 构建 ThinkingEvent
     */
    public static ThinkingEvent fromTracingContext(TracingContext context, String displayTitle) {
        String toolName = context.getToolName();

        // 仅 ontologyQuery 工具需要传递 resultData 给前端展示
        boolean includeResultData = "ontologyQuery".equals(toolName);

        return ThinkingEvent.builder()
                .nodeName("tool_call")
                .displayTitle("工具调用")
                .stepTitle(context.getStepTitle() != null ? context.getStepTitle() : toolName)
                .toolName(toolName)
                .params(context.getParams())
                .paramsDescription(buildParamsDescription(context.getParams()))
                .resultSummary(buildResultSummary(context.getResult(), context.getRecordCount()))
                .recordCount(context.getRecordCount())
                .resultData(includeResultData ? extractResultData(context.getResult()) : null)
                .duration(context.getDuration())
                .success(context.isSuccess())
                .errorMessage(context.getErrorMessage())
                .build();
    }

    /**
     * 构建参数描述（将参数值转为可读字符串）
     */
    private static Map<String, String> buildParamsDescription(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        java.util.HashMap<String, String> desc = new java.util.HashMap<>();
        for (var entry : params.entrySet()) {
            Object value = entry.getValue();
            String strValue = value != null ? value.toString() : "null";
            // 过长时截断
            if (strValue.length() > 30) {
                strValue = strValue.substring(0, 30) + "...";
            }
            desc.put(entry.getKey(), strValue);
        }
        return desc;
    }

    /**
     * 构建结果摘要
     */
    private static String buildResultSummary(Object result, Integer recordCount) {
        if (result == null) {
            return "无结果";
        }
        if (recordCount != null && recordCount > 0) {
            return recordCount + " 条记录";
        }
        // 尝试从结果中提取记录数
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(result);
            if (json.contains("\"records\"")) {
                Map<?, ?> map = mapper.readValue(json, Map.class);
                Object records = map.get("records");
                if (records instanceof java.util.List<?> list) {
                    return list.size() + " 条记录";
                }
            }
        } catch (Exception ignored) {
        }
        return "查询成功";
    }

    /**
     * 提取结果数据
     */
    private static Object extractResultData(Object result) {
        // 对于 ontologyQuery 工具，返回结果数据供前端展示
        if (result == null) {
            return null;
        }
        try {
            // 如果是字符串，尝试解析为 JSON 对象，以便前端格式化展示
            if (result instanceof String str) {
                if (str.startsWith("{") || str.startsWith("[")) {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    return mapper.readValue(str, Object.class);
                }
            }
            // 如果已经是 Map，直接返回
            if (result instanceof Map) {
                return result;
            }
            // 如果是 List，也直接返回
            if (result instanceof java.util.List) {
                return result;
            }
        } catch (Exception e) {
            // 解析失败，返回原始值
        }
        return result;
    }
}