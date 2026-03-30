package com.yycome.sreagent.infrastructure.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thinking 上下文持有者
 * 用于在 AOP 切面中获取当前会话的 SSE sink 和步骤计数器
 *
 * 使用 AtomicReference 而非 ThreadLocal，因为 Spring AI 工具调用发生在
 * Reactor 的 boundedElastic 线程，与设置上下文的 executor 线程不同。
 */
public class ThinkingContextHolder {

    private static final AtomicReference<ThinkingContext> activeContext = new AtomicReference<>();

    public static class ThinkingContext {
        private final Sinks.Many<ServerSentEvent<String>> sink;
        private int stepNumber;

        public ThinkingContext(Sinks.Many<ServerSentEvent<String>> sink, int startStep) {
            this.sink = sink;
            this.stepNumber = startStep;
        }

        public Sinks.Many<ServerSentEvent<String>> getSink() {
            return sink;
        }

        public int nextStep() {
            return ++stepNumber;
        }

        public int getStepNumber() {
            return stepNumber;
        }
    }

    /**
     * 设置当前会话的 Thinking 上下文（线程无关）
     */
    public static void set(Sinks.Many<ServerSentEvent<String>> sink) {
        activeContext.set(new ThinkingContext(sink, 0));
    }

    /**
     * 获取当前会话的 Thinking 上下文（任意线程可调用）
     */
    public static ThinkingContext get() {
        return activeContext.get();
    }

    /**
     * 清除当前会话的 Thinking 上下文
     */
    public static void clear() {
        activeContext.set(null);
    }
}
