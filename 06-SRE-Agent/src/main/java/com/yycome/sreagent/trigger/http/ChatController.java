package com.yycome.sreagent.trigger.http;

import com.yycome.sreagent.config.SREAgentGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的 Chat SSE 接口，供 07-ChatUI 前端对接。
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private SREAgentGraph sreAgentGraph;

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        log.info("收到请求: {}", req.message());

        try {
            Flux<Message> messageFlux = sreAgentGraph.streamMessages(req.message());

            // 收集所有非空文本，合并成一个 SSE 事件
            List<String> allText = new ArrayList<>();

            return messageFlux
                .doOnNext(msg -> log.info("Agent 输出: {}", msg))
                .doOnError(e -> log.error("Agent 错误", e))
                .doOnComplete(() -> log.info("Agent 完成"))
                .filter(msg -> msg instanceof AssistantMessage am && StringUtils.hasText(am.getText()))
                .map(msg -> {
                    String text = ((AssistantMessage) msg).getText();
                    allText.add(text);
                    return text; // 临时，返回单个
                })
                .flatMap(text -> Flux.just(ServerSentEvent.builder(text).build()))
                .doOnCancel(() -> log.info("客户端断开"))
                .onErrorResume(e -> {
                    log.error("SSE 错误", e);
                    return Mono.just(ServerSentEvent.builder("[错误] " + e.getMessage()).build());
                });
        } catch (Exception e) {
            log.error("请求处理异常", e);
            return Flux.just(ServerSentEvent.builder("[错误] " + e.getMessage()).build());
        }
    }

    public record ChatRequest(String message) {}
}