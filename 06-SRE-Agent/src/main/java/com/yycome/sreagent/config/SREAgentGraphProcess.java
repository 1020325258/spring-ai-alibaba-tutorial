package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
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
 * 参考 deepresearch/GraphProcess 实现：
 * - 使用 CompiledGraph.fluxStream() 获取节点输出流
 * - 区分 StreamingOutput（LLM token 流式输出）和普通 NodeOutput
 * - 将输出转换为 SSE 格式发送给前端
 * - 支持 JSON 格式的 thinking/conclusion 事件类型
 */
public class SREAgentGraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(SREAgentGraphProcess.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 步骤计数器，用于生成步骤序号 */
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
     * 处理图执行流，将 NodeOutput 转换为 SSE 事件
     */
    public void processStream(String sessionId, Flux<NodeOutput> generator,
                              Sinks.Many<ServerSentEvent<String>> sink) {
        final String sessionIdStr = sessionId;
        Future<?> future = executor.submit(() -> {
            // 在 executor 线程上设置 ThinkingContextHolder，供 ObservabilityAspect 使用
            ThinkingContextHolder.set(sink);
            generator.doOnNext(output -> {
                String nodeName = output.node();
                String content;
                if (output instanceof StreamingOutput streamingOutput) {
                    logger.debug("Streaming output from node {}: {}", nodeName, sessionId);
                    content = buildStreamingContent(nodeName, streamingOutput, output);
                } else {
                    logger.debug("Normal output from node {}", nodeName);
                    content = buildNormalContent(nodeName, output);
                }
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
     * 处理 LLM 流式输出（token 级别）
     */
    private String buildStreamingContent(String nodeName, StreamingOutput streamingOutput, NodeOutput output) {
        if (streamingOutput == null) return null;
        String textContent = streamingOutput.chunk();
        // chunk 为空时，fallback 到 state
        if (textContent == null || textContent.isEmpty()) {
            // router 节点：输出路由决策
            if ("router".equals(nodeName)) {
                Object routingTarget = output.state().value("routingTarget").orElse("queryAgent");
                return buildThinkingJson("**[路由器]** 路由至 **" + routingTarget + "**");
            }
            // Agent 节点：AgentNode 写入了完整结果
            if ("queryAgent".equals(nodeName) || "investigateAgent".equals(nodeName) || "admin".equals(nodeName)) {
                Object stateResult = output.state().value("result").orElse(null);
                if (stateResult != null && !stateResult.toString().isEmpty()) {
                    return stateResult.toString();
                }
            }
            return null;
        }
        return textContent;
    }

    /**
     * 处理普通节点输出（公开，供测试使用）
     */
    public String buildNormalContent(String nodeName, NodeOutput output) {
        // 跳过 START 和 END 节点
        if ("__start__".equals(nodeName) || "__end__".equals(nodeName)) {
            return null;
        }
        // router 和 Agent 节点由 buildStreamingContent 处理（StreamingOutput 包裹）
        if ("router".equals(nodeName) || "queryAgent".equals(nodeName)
                || "investigateAgent".equals(nodeName) || "admin".equals(nodeName)) {
            return null;
        }
        return null;
    }

    /**
     * 构建 JSON 格式的 thinking 事件
     * 用于实时输出排查过程
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
     * 用于输出最终结论
     */
    public String buildConclusionJson(String content) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of("type", "conclusion", "content", content));
        } catch (JsonProcessingException e) {
            logger.error("Error building conclusion JSON", e);
            return "{\"type\": \"conclusion\", \"content\": \"\"}";
        }
    }

    /**
     * 获取下一个步骤序号
     */
    public int nextStepNumber() {
        return stepCounter.incrementAndGet();
    }

    /**
     * 重置步骤计数器（每个新会话调用）
     */
    public void resetStepCounter() {
        stepCounter.set(0);
    }

    /**
     * 终止运行中的图
     */
    public boolean stopGraph(String sessionId) {
        Future<?> future = this.graphTaskFutureMap.remove(sessionId);
        if (future == null) {
            return false;
        }
        if (future.isDone()) {
            return true;
        }
        return future.cancel(true);
    }

    /**
     * 获取图的当前状态
     */
    public StateSnapshot getState(RunnableConfig runnableConfig) {
        return compiledGraph.getState(runnableConfig);
    }

    /**
     * 同步执行图，返回累积的文本内容
     * 供测试和简单调用场景使用
     */
    public String streamAndCollect(String input) {
        Map<String, Object> inputs = Map.of("input", input);
        StringBuilder sb = new StringBuilder();
        compiledGraph.stream(inputs, RunnableConfig.builder().build())
                .doOnNext(output -> {
                    String nodeName = output.node();
                    String content;
                    if (output instanceof StreamingOutput so) {
                        content = buildStreamingContent(nodeName, so, output);
                    } else {
                        content = buildNormalContent(nodeName, output);
                    }
                    if (content != null && !content.isEmpty()) {
                        sb.append(content);
                    }
                })
                .blockLast();
        return sb.toString();
    }

}