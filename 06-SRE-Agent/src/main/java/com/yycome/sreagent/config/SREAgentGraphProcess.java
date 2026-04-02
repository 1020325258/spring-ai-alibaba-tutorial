package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.config.node.SREAgentNodeName;
import com.yycome.sreagent.infrastructure.service.ThinkingContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SRE-Agent 图执行处理器
 *
 * <p>统一使用 SREAgentEventDispatcher 进行事件分发：
 * - 基于 nodeName 差异化构建 SSE 事件
 * - 工具事件从 ThinkingContextHolder 统一发送
 */
public class SREAgentGraphProcess {

    private static final Logger logger = LoggerFactory.getLogger(SREAgentGraphProcess.class);

    private final ObjectMapper objectMapper;
    private final SREAgentEventDispatcher eventDispatcher;
    private final MessageWindowChatMemory memory;

    private final ConcurrentHashMap<String, Future<?>> graphTaskFutureMap = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    private final CompiledGraph compiledGraph;

    public SREAgentGraphProcess(CompiledGraph compiledGraph, ObjectMapper objectMapper,
                                 MessageWindowChatMemory memory) {
        this.compiledGraph = compiledGraph;
        this.objectMapper = objectMapper;
        this.eventDispatcher = new SREAgentEventDispatcher(objectMapper);
        this.memory = memory;
    }

    public CompiledGraph compiledGraph() {
        return compiledGraph;
    }

    /**
     * 处理图执行流，将 NodeOutput 转换为 SSE 事件。
     * 使用 SREAgentEventDispatcher 统一分发。
     * 注意：工具调用事件由 ObservabilityAspect 实时发送，无需在此处理。
     */
    public void processStream(String sessionId, Flux<NodeOutput> generator,
                              Sinks.Many<ServerSentEvent<String>> sink) {
        final String sessionIdStr = sessionId;
        final AtomicReference<OverAllState> finalStateRef = new AtomicReference<>();
        Future<?> future = executor.submit(() -> {
            ThinkingContextHolder.set(sink);
            generator.doOnNext(output -> {
                String nodeName = output.node();

                // 特殊节点不输出事件
                if (StateGraph.START.equals(nodeName) || StateGraph.END.equals(nodeName)) {
                    return;
                }

                // 发送节点事件
                eventDispatcher.dispatch(output, sink);
                finalStateRef.set(output.state());

            }).doOnComplete(() -> {
                writeBackToMemory(sessionIdStr, finalStateRef.get());
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
     * 将本轮对话写回 MessageWindowChatMemory
     * sessionId 为空时跳过（集成测试中 SREAgentGraphProcess.streamAndCollect 不传 sessionId）
     */
    private void writeBackToMemory(String sessionId, OverAllState state) {
        if (sessionId == null || sessionId.isEmpty() || state == null) {
            return;
        }
        String input = state.value("input", "");
        String result = state.value("result", "");
        if (!input.isEmpty() && !result.isEmpty()) {
            memory.add(sessionId, List.of(new UserMessage(input), new AssistantMessage(result)));
            logger.info("[Memory] write-back sessionId={}, input_len={}, result_len={}",
                    sessionId, input.length(), result.length());
        }
    }

    /**
     * 同步执行图，返回累积的文本内容（供测试使用）
     * 简化逻辑：只调用 stream 获取输出，不处理工具事件发送
     */
    public String streamAndCollect(String input) {
        Map<String, Object> inputs = Map.of("input", input);
        StringBuilder sb = new StringBuilder();

        // 创建一个 sink 用于收集输出
        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 设置上下文，确保 ObservabilityAspect 能正常发送工具事件
        ThinkingContextHolder.set(sink);

        // 调用 stream 获取输出
        compiledGraph.stream(inputs, RunnableConfig.builder().build())
                .doOnNext(output -> {
                    String nodeName = output.node();

                    // 特殊节点不输出
                    if (StateGraph.START.equals(nodeName) || StateGraph.END.equals(nodeName)) {
                        return;
                    }

                    // 发送节点事件
                    eventDispatcher.dispatch(output, sink);
                })
                .doOnComplete(ThinkingContextHolder::clear)
                .doOnError(e -> {
                    ThinkingContextHolder.clear();
                })
                .blockLast();

        // 收集输出
        sink.asFlux()
                .doOnNext(event -> {
                    if (event.data() != null) {
                        sb.append(event.data());
                    }
                })
                .blockLast();

        return sb.toString();
    }
}