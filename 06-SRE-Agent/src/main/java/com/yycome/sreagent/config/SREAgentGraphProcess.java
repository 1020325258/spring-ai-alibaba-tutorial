package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.yycome.sreagent.infrastructure.service.ThinkingContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SRE-Agent 图执行处理器
 *
 * <p>参考 04-AnalysisAgent/GraphProcessor.java 的设计：直接按 nodeName 从 state 读取结果，
 * 不做 instanceof StreamingOutput 判断。
 *
 * <p>原因：框架的 GraphRunnerContext.buildNodeOutput() 无论节点类型都包装成 StreamingOutput，
 * 且本图所有节点（AgentNode 内部 blockLast、QueryAgentNode 同步调用）的 chunk 始终为 null，
 * instanceof 判断无实际意义。
 */
public class SREAgentGraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(SREAgentGraphProcess.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AtomicInteger stepCounter = new AtomicInteger(0);

    private final ConcurrentHashMap<String, Future<?>> graphTaskFutureMap = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    private final CompiledGraph compiledGraph;

    public SREAgentGraphProcess(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    public CompiledGraph compiledGraph() {
        return compiledGraph;
    }

    /**
     * 处理图执行流，将 NodeOutput 转换为 SSE 事件。
     * 直接按 nodeName 从 state 读取结果，参考 04-AnalysisAgent/GraphProcessor.java。
     */
    public void processStream(String sessionId, Flux<NodeOutput> generator,
                              Sinks.Many<ServerSentEvent<String>> sink) {
        final String sessionIdStr = sessionId;
        Future<?> future = executor.submit(() -> {
            ThinkingContextHolder.set(sink);
            generator.doOnNext(output -> {
                String content = resolveContent(output);
                if (content != null && !content.isEmpty()) {
                    sink.tryEmitNext(ServerSentEvent.builder(content).build());
                }
            }).doOnComplete(() -> {
                logger.info("Stream processing completed for session: {}", sessionIdStr);
                ThinkingContextHolder.clear();
                sink.tryEmitComplete();
                graphTaskFutureMap.remove(sessionIdStr);
            }).doOnError(e -> {
                logger.error("Error in stream processing", e);
                ThinkingContextHolder.clear();
                sink.tryEmitNext(ServerSentEvent.builder("{\"error\": \"" + e.getMessage() + "\"}").build());
                sink.tryEmitError(e);
            }).subscribe();
        });
        Future<?> oldFuture = graphTaskFutureMap.put(sessionIdStr, future);
        if (oldFuture != null && !oldFuture.isDone()) {
            logger.warn("A task with the same sessionId {} is still running!", sessionIdStr);
        }
    }

    /**
     * 按 nodeName 将节点输出转换为 SSE 内容字符串（公开，供测试使用）。
     *
     * <ul>
     *   <li>router     → thinking 事件（路由决策）</li>
     *   <li>queryAgent → conclusion 事件（JSON 代码块，来自 QueryAgentNode 写入的 state["result"]）</li>
     *   <li>investigateAgent / admin → 直接返回 state["result"]（LLM 生成的 Markdown 文本）</li>
     *   <li>START / END 及其他节点 → null（不输出）</li>
     * </ul>
     */
    public String resolveContent(NodeOutput output) {
        String nodeName = output.node();

        if (StateGraph.START.equals(nodeName) || StateGraph.END.equals(nodeName)) {
            return null;
        }

        if ("router".equals(nodeName)) {
            Object routingTarget = output.state().value("routingTarget").orElse("queryAgent");
            return buildThinkingJson("**[路由器]** 路由至 **" + routingTarget + "**");
        }

        if ("queryAgent".equals(nodeName)) {
            Object stateResult = output.state().value("result").orElse(null);
            if (stateResult == null || stateResult.toString().isEmpty()) return null;
            return buildConclusionJson("```json\n" + prettyPrintJson(stateResult.toString()) + "\n```");
        }

        if ("investigateAgent".equals(nodeName) || "admin".equals(nodeName)) {
            Object stateResult = output.state().value("result").orElse(null);
            if (stateResult != null && !stateResult.toString().isEmpty()) {
                return stateResult.toString();
            }
        }

        return null;
    }

    /**
     * 构建 JSON 格式的 thinking 事件
     */
    public String buildThinkingJson(String content) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("type", "thinking", "content", content));
        } catch (JsonProcessingException e) {
            logger.error("Error building thinking JSON", e);
            return "{\"type\": \"thinking\", \"content\": \"\"}";
        }
    }

    /**
     * 构建 JSON 格式的 conclusion 事件
     */
    public String buildConclusionJson(String content) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("type", "conclusion", "content", content));
        } catch (JsonProcessingException e) {
            logger.error("Error building conclusion JSON", e);
            return "{\"type\": \"conclusion\", \"content\": \"\"}";
        }
    }

    private String prettyPrintJson(String raw) {
        try {
            Object node = OBJECT_MAPPER.readValue(raw, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return raw;
        }
    }

    public int nextStepNumber() {
        return stepCounter.incrementAndGet();
    }

    public void resetStepCounter() {
        stepCounter.set(0);
    }

    public boolean stopGraph(String sessionId) {
        Future<?> future = this.graphTaskFutureMap.remove(sessionId);
        if (future == null) return false;
        if (future.isDone()) return true;
        return future.cancel(true);
    }

    public StateSnapshot getState(RunnableConfig runnableConfig) {
        return compiledGraph.getState(runnableConfig);
    }

    /**
     * 同步执行图，返回累积的文本内容（供测试使用）
     */
    public String streamAndCollect(String input) {
        Map<String, Object> inputs = Map.of("input", input);
        StringBuilder sb = new StringBuilder();
        compiledGraph.stream(inputs, RunnableConfig.builder().build())
                .doOnNext(output -> {
                    String content = resolveContent(output);
                    if (content != null && !content.isEmpty()) {
                        sb.append(content);
                    }
                })
                .blockLast();
        return sb.toString();
    }

}
