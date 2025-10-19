package com.alibaba.yycome.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.yycome.service.GraphProcessor;
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

@RestController
public class AnalysisController {

    private final Logger logger = LoggerFactory.getLogger(AnalysisController.class);

    private final CompiledGraph compiledGraph;

    public AnalysisController(@Qualifier("analysisGraph") StateGraph analysisGraph) throws GraphStateException {
        this.compiledGraph = analysisGraph.compile();
    }

    @GetMapping("/analysis")
    public Flux<ServerSentEvent<String>> analysis(@RequestParam("query") String query) {
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", query);

        // 执行图，获取 Flux 流
        Flux<NodeOutput> nodeOutputFlux = compiledGraph.fluxStream(inputs);

        // 处理节点输出的 Flux 流，给前端响应结果
        GraphProcessor graphProcessor = new GraphProcessor(nodeOutputFlux, sink);
        graphProcessor.process();

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
