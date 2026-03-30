package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thinking 内容输出服务
 * 从 TracingService 读取工具调用步骤，构建 Thinking 块 Markdown 用于流式输出
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinkingOutputService {

    private static final int MAX_RESULT_LENGTH = 300;
    private final ObjectMapper objectMapper;

    /**
     * 构建完整的 Thinking 块 Markdown
     */
    public String buildThinkingBlock(ConcurrentLinkedDeque<TracingContext> traces) {
        if (traces == null || traces.isEmpty()) {
            return "";
        }

        // 按时间正序排列（最新的在前面，所以需要反转）
        List<TracingContext> orderedTraces = new ArrayList<>(traces);
        java.util.Collections.reverse(orderedTraces);

        StringBuilder sb = new StringBuilder();
        sb.append("<thinking>\n");

        int stepNumber = 1;
        for (TracingContext context : orderedTraces) {
            sb.append(buildSingleStep(context, stepNumber++));
            sb.append("\n");
        }

        sb.append("</thinking>");
        return sb.toString();
    }

    /**
     * 构建单个步骤的 Markdown
     */
    private String buildSingleStep(TracingContext context, int stepNumber) {
        StringBuilder sb = new StringBuilder();

        // 步骤标题（简短）
        String title = context.getStepTitle() != null ? context.getStepTitle() : context.getToolName();
        sb.append(String.format("**步骤%d - %s**\n", stepNumber, title));

        // 工具信息
        String toolName = context.getToolName();
        sb.append(String.format("> 工具：`%s`\n", toolName != null ? toolName : "unknown"));

        // 参数（简化显示，只显示关键参数）
        if (context.getParams() != null && !context.getParams().isEmpty()) {
            sb.append("> 参数：\n");
            for (var entry : context.getParams().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // 参数值太长则截断
                String valueStr = value != null ? value.toString() : "null";
                if (valueStr.length() > 30) {
                    valueStr = valueStr.substring(0, 30) + "...";
                }
                sb.append(String.format("> - %s: `%s`\n", key, valueStr));
            }
        }

        // 结果统计（不显示完整 JSON，只显示记录数和关键信息）
        if (context.getResult() != null) {
            String summary = buildResultSummary(context.getResult(), context.getRecordCount());
            sb.append("> ").append(summary).append("\n");
        }

        // 耗时和状态
        long duration = context.getDuration();
        String status = context.isSuccess() ? "✓" : "✗";
        sb.append(String.format("> 耗时：%dms | 成功：%s", duration, status));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * 构建结果摘要（不显示完整 JSON）
     */
    private String buildResultSummary(Object result, Integer recordCount) {
        try {
            String json = objectMapper.writeValueAsString(result);

            // 尝试解析 JSON 获取记录数
            if (recordCount != null && recordCount > 0) {
                return String.format("结果：%d 条记录", recordCount);
            }

            // 从 JSON 中提取关键信息
            if (json.contains("records")) {
                // 查找 records 数组长度
                int recordsStart = json.indexOf("\"records\"");
                if (recordsStart >= 0) {
                    int bracketStart = json.indexOf("[", recordsStart);
                    int bracketEnd = json.indexOf("]", bracketStart);
                    if (bracketStart >= 0 && bracketEnd > bracketStart) {
                        String recordsContent = json.substring(bracketStart + 1, bracketEnd);
                        // 简单估算：逗号分隔的对象
                        long count = recordsContent.chars().filter(c -> c == '{').count();
                        if (count > 0) {
                            return String.format("结果：%d 条记录", count);
                        }
                    }
                }
            }

            // 默认返回摘要
            if (json.length() > 50) {
                return "结果：数据已返回";
            }
            return "结果：OK";
        } catch (Exception e) {
            return "结果：OK";
        }
    }

    /**
     * 序列化结果，截断过长内容（不再显示完整 JSON）
     */
    private String serializeResult(Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            if (json.length() > MAX_RESULT_LENGTH) {
                return json.substring(0, MAX_RESULT_LENGTH) + "\n... (truncated)";
            }
            return json;
        } catch (JsonProcessingException e) {
            return result.toString();
        }
    }
}
