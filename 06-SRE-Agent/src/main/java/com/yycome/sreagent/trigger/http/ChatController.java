package com.yycome.sreagent.trigger.http;

import com.yycome.sreagent.config.SREAgentGraphProcess;
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

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        String sessionId = UUID.randomUUID().toString();
        log.info("收到请求: {}, sessionId: {}", req.message(), sessionId);

        try {
            // 创建 SSE sink
            Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

            // 重置步骤计数器
            graphProcess.resetStepCounter();

            // 构建输入
            java.util.Map<String, Object> inputs = java.util.Map.of("input", req.message());

            // 使用 CompiledGraph.stream() 获取节点输出流
            var resultFuture = graphProcess.compiledGraph().stream(inputs, RunnableConfig.builder().build());

            // 处理流
            graphProcess.processStream(sessionId, resultFuture, sink);

            return sink.asFlux()
                    .doOnCancel(() -> {
                        log.info("客户端断开: {}", sessionId);
                        ThinkingContextHolder.clear();
                    })
                    .doOnComplete(() -> ThinkingContextHolder.clear())
                    .onErrorResume(e -> {
                        log.error("SSE 错误: {}", e.getMessage());
                        ThinkingContextHolder.clear();
                        return Flux.just(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
                    });
        } catch (Exception e) {
            log.error("请求处理异常: {}", e.getMessage(), e);
            ThinkingContextHolder.clear();
            return Flux.just(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
        }
    }

    public record ChatRequest(String message) {}
}