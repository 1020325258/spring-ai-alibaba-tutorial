package com.alibaba.yycome;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@SpringBootTest
public class Test {

    @Qualifier("analysisGraph")
    @Autowired
    public StateGraph analysisGraph;

    Logger logger = LoggerFactory.getLogger(Test.class);

    @org.junit.jupiter.api.Test
    public void test_graph() throws GraphStateException {

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        CompiledGraph compile = analysisGraph.compile();
        Flux<NodeOutput> nodeOutputFlux = compile.fluxStream();
        Disposable errorInStreamProcessing = nodeOutputFlux.doOnNext(output -> {
            String nodeName = output.node();
            String content = "";
            if (nodeName.equals("analysis")) {
                content = output.state().value("analysis_content", "");
            }
            logger.info("node name:" + nodeName + " content:" + content);
            sink.tryEmitNext(ServerSentEvent.builder(nodeName + "处理结果:" + content).build());
        }).doOnComplete(() -> {
            logger.info("Stream processing completed.");
            sink.tryEmitComplete();
        }).doOnError(e -> {
            logger.error("Error in stream processing", e);
            sink.tryEmitError(e);
        }).subscribe();
    }
}
