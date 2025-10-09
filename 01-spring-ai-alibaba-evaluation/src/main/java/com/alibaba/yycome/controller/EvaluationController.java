package com.alibaba.yycome.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
public class EvaluationController {

    private Logger logger = LoggerFactory.getLogger(EvaluationController.class);

    private final CompiledGraph compiledGraph;

    public EvaluationController(@Qualifier("evaluationGraph")StateGraph evaluationGraph) throws GraphStateException {
        this.compiledGraph = evaluationGraph.compile();
    }

    @GetMapping("/evaluation")
    public Flux<ServerSentEvent<String>> evaluate(@RequestParam("query") String query, @RequestParam("answer") String answer) {

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", query);
        inputs.put("answer", answer);

        // 执行图，获取 Flux 流
        Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(inputs);
        CompletableFuture.runAsync(() -> {
            // 处理 Flux 流，从 State 获取各个节点的输出
            nodeOutputFlux.doOnNext(output -> {
                String nodeName = output.node();
                String content = "";
                if (nodeName.equals("evaluation")) {
                    content = output.state().value("evaluation_content", "");
                }
                logger.info("node name:" + nodeName + " content:" + content);
                sink.tryEmitNext(ServerSentEvent.builder(nodeName + "处理结果:" + content).build());
            }).doOnComplete(() -> {
                logger.info("Stream processing completed.");
                sink.tryEmitComplete();
            }).doOnError(e -> {
                logger.error("Error in stream processing", e);
                sink.tryEmitNext(
                        // 服务端异常时，给前端发送消息
                        ServerSentEvent.builder("服务端处理异常")
                                .build());
                sink.tryEmitError(e);
            }).subscribe();
        });

        return sink.asFlux()
                .doOnCancel(() -> logger.info("Client disconnected from stream"))
                .onErrorResume(throwable -> {
                    logger.error("Error occurred during streaming", throwable);
                    return Mono.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("Error occurred during streaming: " + throwable.getMessage())
                            .build());
                });
    }
}
