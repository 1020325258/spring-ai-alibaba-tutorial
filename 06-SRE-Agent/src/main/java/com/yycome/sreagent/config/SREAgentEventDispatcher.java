package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.config.node.SREAgentNodeName;
import com.yycome.sreagent.infrastructure.service.ThinkingContextHolder;
import com.yycome.sreagent.infrastructure.service.model.ThinkingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * SRE-Agent 统一事件分发器
 * 基于 nodeName 差异化构建 SSE 事件
 */
public class SREAgentEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SREAgentEventDispatcher.class);

    private final ObjectMapper objectMapper;

    public SREAgentEventDispatcher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 统一的事件分发方法
     * 所有 SSE 事件都从这里发出
     */
    public void dispatch(NodeOutput output, Sinks.Many<ServerSentEvent<String>> sink) {
        String nodeName = output.node();
        SREAgentNodeName node = SREAgentNodeName.fromNodeName(nodeName);

        if (node == null) {
            log.debug("未找到对应的节点枚举，nodeName: {}", nodeName);
            return;
        }

        // 特殊处理 START/END，不输出事件
        if (node == SREAgentNodeName.START || node == SREAgentNodeName.END) {
            return;
        }

        String eventJson = null;
        try {
            eventJson = switch (node) {
                case ROUTER -> buildRoutingEvent(output);
                case TOOL_CALL -> buildToolCallEvent(output);
                case QUERY_AGENT -> buildConclusionEvent(output);
                case INVESTIGATE_AGENT, ADMIN -> buildMarkdownEvent(output);
                default -> null;
            };
        } catch (Exception e) {
            log.error("构建事件失败，nodeName: {}", nodeName, e);
        }

        if (eventJson != null) {
            sink.tryEmitNext(ServerSentEvent.builder(eventJson).build());
            log.debug("发送事件，nodeName: {}, event: {}", nodeName, eventJson);
        }
    }

    /**
     * 构建路由事件（router 节点）
     */
    private String buildRoutingEvent(NodeOutput output) throws JsonProcessingException {
        Object routingTarget = output.state().value("routingTarget").orElse("queryAgent");

        ThinkingEvent event = ThinkingEvent.builder()
                .nodeName("router")
                .displayTitle("意图识别")
                .stepTitle("路由至 " + routingTarget)
                .toolName("router")
                .params(Map.of("target", routingTarget.toString()))
                .paramsDescription(Map.of("target", routingTarget.toString()))
                .resultSummary("路由成功")
                .duration(0L)
                .success(true)
                .build();

        return objectMapper.writeValueAsString(event);
    }

    /**
     * 构建工具调用事件（tool_call 节点）
     * 从 ThinkingContextHolder 获取累积的工具事件
     */
    private String buildToolCallEvent(NodeOutput output) throws JsonProcessingException {
        ThinkingContextHolder.ThinkingContext ctx = ThinkingContextHolder.get();
        if (ctx == null) {
            log.warn("[SREAgentEventDispatcher] 上下文为 null，无法获取工具事件");
            return null;
        }

        // 获取累积的工具事件并发送
        var toolEvents = ctx.getToolEvents();
        if (toolEvents.isEmpty()) {
            log.debug("[SREAgentEventDispatcher] 无工具事件需要发送");
            return null;
        }

        // 发送所有工具事件
        for (ThinkingEvent event : toolEvents) {
            String json = objectMapper.writeValueAsString(event);
            // 这里通过 sink 发送，但当前方法返回值是单个事件
            // 实际发送逻辑在调用处处理
            log.debug("[SREAgentEventDispatcher] 发送工具事件: {}", json);
        }

        // 清空工具事件，避免重复发送
        ctx.clearToolEvents();

        // 返回第一个事件作为代表
        return objectMapper.writeValueAsString(toolEvents.get(0));
    }

    /**
     * 构建结论事件（queryAgent 节点）
     */
    private String buildConclusionEvent(NodeOutput output) throws JsonProcessingException {
        Object stateResult = output.state().value("result").orElse(null);
        if (stateResult == null || stateResult.toString().isEmpty()) {
            return null;
        }

        String content = stateResult.toString();
        // 如果是 JSON 字符串，包装为代码块
        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            content = "```json\n" + prettyPrintJson(content) + "\n```";
        }

        Map<String, Object> event = Map.of(
                "nodeName", "queryAgent",
                "displayTitle", "数据查询",
                "content", content
        );

        return objectMapper.writeValueAsString(event);
    }

    /**
     * 构建 Markdown 事件（investigateAgent / admin 节点）
     */
    private String buildMarkdownEvent(NodeOutput output) throws JsonProcessingException {
        Object stateResult = output.state().value("result").orElse(null);
        if (stateResult == null || stateResult.toString().isEmpty()) {
            return null;
        }

        String nodeName = output.node();
        String displayTitle = SREAgentNodeName.getDisplayTitleByNodeName(nodeName);

        Map<String, Object> event = Map.of(
                "nodeName", nodeName,
                "displayTitle", displayTitle,
                "content", stateResult.toString()
        );

        return objectMapper.writeValueAsString(event);
    }

    /**
     * 格式化 JSON
     */
    private String prettyPrintJson(String raw) {
        try {
            Object node = objectMapper.readValue(raw, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * 发送工具事件列表（从上下文获取并发送）
     */
    public void publishToolEvents(Sinks.Many<ServerSentEvent<String>> sink) {
        ThinkingContextHolder.ThinkingContext ctx = ThinkingContextHolder.get();
        if (ctx == null) {
            return;
        }

        var toolEvents = ctx.getToolEvents();
        for (ThinkingEvent event : toolEvents) {
            try {
                String json = objectMapper.writeValueAsString(event);
                sink.tryEmitNext(ServerSentEvent.builder(json).build());
                log.debug("[SREAgentEventDispatcher] 发送工具事件: {}", event.getToolName());
            } catch (JsonProcessingException e) {
                log.error("序列化工具事件失败", e);
            }
        }

        // 清空工具事件
        ctx.clearToolEvents();
    }
}