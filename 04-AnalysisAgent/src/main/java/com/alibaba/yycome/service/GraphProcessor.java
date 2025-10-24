package com.alibaba.yycome.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.fastjson.JSON;
import com.alibaba.yycome.enums.StateKeyEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.CompletableFuture;

public class GraphProcessor {

    private final Logger logger = LoggerFactory.getLogger(GraphProcessor.class);

    private final Flux<NodeOutput> nodeOutputFlux;

    private final Sinks.Many<ServerSentEvent<String>> sink;

    public GraphProcessor(Flux<NodeOutput> nodeOutputFlux, Sinks.Many<ServerSentEvent<String>> sink) {
        this.nodeOutputFlux = nodeOutputFlux;
        this.sink = sink;
    }

    public void process() {
        CompletableFuture.runAsync(() -> {
            // 处理 Flux 流，从 State 获取各个节点的输出
            nodeOutputFlux.doOnNext(output -> {
                String nodeName = output.node();
                String content = "";

                // 如果是开始节点，
                if (StateGraph.START.equals(nodeName)) {
                    content = "START";
                } else if (StateGraph.END.equals(nodeName)) {
                    content = "END";
                } else if (nodeName.equals("planner")) {
                    content = output.state().value(StateKeyEnum.PLANNER_CONTENT.getKey(), "");
                } else if (nodeName.equals("plan_accept")) {
                    content = JSON.toJSONString(output.state().value(StateKeyEnum.PLAN.getKey()));
                } else if (nodeName.equals("search_node")) {
                    content = JSON.toJSONString(output.state().value(StateKeyEnum.SEARCH_CONTENT.getKey()));
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
    }

}
