package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Thinking 事件广播服务
 * 用于在工具调用后实时广播 Thinking 内容到消息流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinkingEventPublisher {

    private final ThinkingOutputService thinkingOutputService;
    private final ObjectMapper objectMapper;

    /**
     * 发布 Thinking 内容
     */
    public void publishThinking(String thinkingContent) {
        // 保留原有方法兼容性
    }

    /**
     * 从单个 TracingContext 构建并发布 Thinking 事件
     * @param context 工具调用追踪上下文
     * @param sink SSE sink 用于发送事件
     * @param stepNumber 步骤序号
     */
    public void publishStepThinking(TracingContext context, Sinks.Many<ServerSentEvent<String>> sink, int stepNumber) {
        if (context == null || sink == null) {
            return;
        }

        try {
            String content = buildSingleStepContent(context, stepNumber);
            String json = objectMapper.writeValueAsString(Map.of("type", "thinking", "content", content));
            sink.tryEmitNext(ServerSentEvent.builder(json).build());
            log.debug("Published thinking event for step {}", stepNumber);
        } catch (JsonProcessingException e) {
            log.error("Error building thinking JSON", e);
        }
    }

    /**
     * 构建单步 Thinking 内容
     */
    private String buildSingleStepContent(TracingContext context, int stepNumber) {
        StringBuilder sb = new StringBuilder();

        // 步骤标题
        String title = context.getStepTitle() != null ? context.getStepTitle() : context.getToolName();
        sb.append(String.format("**步骤%d - %s**\n", stepNumber, title));

        // 工具信息
        String toolName = context.getToolName();
        sb.append(String.format("> 工具：`%s`\n", toolName != null ? toolName : "unknown"));

        // 参数
        if (context.getParams() != null && !context.getParams().isEmpty()) {
            sb.append("> 参数：\n");
            for (var entry : context.getParams().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                String valueStr = value != null ? value.toString() : "null";
                if (valueStr.length() > 30) {
                    valueStr = valueStr.substring(0, 30) + "...";
                }
                sb.append(String.format("> - %s: `%s`\n", key, valueStr));
            }
        }

        // 结果统计
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
     * 构建结果摘要
     */
    private String buildResultSummary(Object result, Integer recordCount) {
        try {
            String json = objectMapper.writeValueAsString(result);

            if (recordCount != null && recordCount > 0) {
                return String.format("结果：%d 条记录", recordCount);
            }

            if (json.contains("records")) {
                int recordsStart = json.indexOf("\"records\"");
                if (recordsStart >= 0) {
                    int bracketStart = json.indexOf("[", recordsStart);
                    int bracketEnd = json.indexOf("]", bracketStart);
                    if (bracketStart >= 0 && bracketEnd > bracketStart) {
                        String recordsContent = json.substring(bracketStart + 1, bracketEnd);
                        long count = recordsContent.chars().filter(c -> c == '{').count();
                        if (count > 0) {
                            return String.format("结果：%d 条记录", count);
                        }
                    }
                }
            }

            if (json.length() > 50) {
                return "结果：数据已返回";
            }
            return "结果：OK";
        } catch (Exception e) {
            return "结果：OK";
        }
    }
}
