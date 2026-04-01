package com.yycome.sreagent.trigger.http;

import com.yycome.sreagent.config.SREAgentGraphProcess;
import com.yycome.sreagent.infrastructure.service.RequestLogService;
import com.yycome.sreagent.infrastructure.service.ThinkingContextHolder;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Chat SSE 接口，参考 deepresearch/ChatController.java 实现
 * 使用 CompiledGraph.fluxStream() + GraphProcess 处理节点输出
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private SREAgentGraphProcess graphProcess;

    @Autowired
    private RequestLogService requestLogService;

    /**
     * 流式处理入口（SSE 接口）
     * 返回 Flux<ServerSentEvent<String>> 用于 SSE 推送
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        return streamFlux(req.message());
    }

    /**
     * 流式处理核心逻辑（返回 Flux，用于 SSE 接口）
     * 封装为独立方法，便于单元测试 mock
     */
    public Flux<ServerSentEvent<String>> streamFlux(String message) {
        String sessionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        log.info("收到请求: {}, sessionId: {}", message, sessionId);

        // 记录请求开始
        requestLogService.logRequestStart(sessionId, message);

        // 用于收集完整响应
        StringBuilder responseBuilder = new StringBuilder();

        try {
            // 创建 SSE sink
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

            // 构建输入
            java.util.Map<String, Object> inputs = java.util.Map.of("input", message);

            // 使用 CompiledGraph.stream() 获取节点输出流
            var resultFuture = graphProcess.compiledGraph().stream(inputs, RunnableConfig.builder().build());

            // 处理流
            graphProcess.processStream(sessionId, resultFuture, sink);

            return sink.asFlux()
                    .doOnNext(event -> {
                        // 收集响应内容
                        if (event.data() != null) {
                            responseBuilder.append(event.data());
                        }
                    })
                    .doOnCancel(() -> {
                        log.info("客户端断开: {}", sessionId);
                        ThinkingContextHolder.clear();
                    })
                    .doOnComplete(() -> {
                        ThinkingContextHolder.clear();
                        // 记录响应完成
                        long duration = System.currentTimeMillis() - startTime;
                        requestLogService.logResponseComplete(sessionId, responseBuilder.toString(), duration);
                    })
                    .onErrorResume(e -> {
                        log.error("SSE 错误: {}", e.getMessage());
                        ThinkingContextHolder.clear();
                        requestLogService.logError(sessionId, e.getMessage());
                        return Flux.just(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
                    });
        } catch (Exception e) {
            log.error("请求处理异常: {}", e.getMessage(), e);
            ThinkingContextHolder.clear();
            requestLogService.logError(sessionId, e.getMessage());
            return Flux.just(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
        }
    }

    /**
     * 同步执行并收集完整输出（用于测试）
     * 将 Flux 流式输出同步收集为字符串返回
     *
     * @param message 用户输入消息
     * @return 累积的完整响应文本
     */
    public String streamAndCollect(String message) {
        return streamFlux(message).collectList()
                .block()
                .stream()
                .map(ServerSentEvent::data)
                .filter(data -> data != null && !data.isEmpty())
                .reduce(String::concat)
                .orElse("");
    }

    public record ChatRequest(String message) {}
}