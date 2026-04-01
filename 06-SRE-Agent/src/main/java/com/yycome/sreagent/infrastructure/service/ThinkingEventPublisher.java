package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import com.yycome.sreagent.infrastructure.service.model.ThinkingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

/**
 * Thinking 事件广播服务
 * 用于在工具调用后实时广播 Thinking 内容到消息流
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThinkingEventPublisher {

    private final ObjectMapper objectMapper;

    /**
     * 发布结构化 Thinking 事件
     * @param context 工具调用追踪上下文
     * @param sink SSE sink 用于发送事件
     * @param displayTitle 步骤标题
     */
    public void publishStepThinking(TracingContext context, Sinks.Many<ServerSentEvent<String>> sink, String displayTitle) {
        if (context == null || sink == null) {
            log.warn("[ThinkingEventPublisher] context 或 sink 为 null，跳过发布");
            return;
        }

        try {
            // 使用结构化对象
            ThinkingEvent event = ThinkingEvent.fromTracingContext(context, displayTitle);
            String json = objectMapper.writeValueAsString(event);
            sink.tryEmitNext(ServerSentEvent.builder(json).build());
            log.info("[ThinkingEventPublisher] 发布 thinking 事件: {} - {} - {}", displayTitle, context.getToolName(), json);
        } catch (JsonProcessingException e) {
            log.error("Error building thinking JSON", e);
        }
    }
}
