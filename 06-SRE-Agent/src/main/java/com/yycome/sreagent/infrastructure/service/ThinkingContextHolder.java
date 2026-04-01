package com.yycome.sreagent.infrastructure.service;

import com.yycome.sreagent.infrastructure.service.model.ThinkingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thinking 上下文持有者
 * 用于在 AOP 切面中获取当前会话的 SSE sink 和步骤计数器
 *
 * 使用 AtomicReference 而非 ThreadLocal，因为 Spring AI 工具调用发生在
 * Reactor 的 boundedElastic 线程，与设置上下文的 executor 线程不同。
 */
public class ThinkingContextHolder {

    private static final Logger log = LoggerFactory.getLogger(ThinkingContextHolder.class);

    private static final AtomicReference<ThinkingContext> activeContext = new AtomicReference<>();

    public static class ThinkingContext {
        private final Sinks.Many<ServerSentEvent<String>> sink;
        private int stepNumber;
        private final String threadName;
        private final List<ThinkingEvent> toolEvents = new ArrayList<>();

        public ThinkingContext(Sinks.Many<ServerSentEvent<String>> sink, int startStep) {
            this.sink = sink;
            this.stepNumber = startStep;
            this.threadName = Thread.currentThread().getName();
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

        public String getThreadName() {
            return threadName;
        }

        public void addToolEvent(ThinkingEvent event) {
            this.toolEvents.add(event);
        }

        public List<ThinkingEvent> getToolEvents() {
            return toolEvents;
        }

        public void clearToolEvents() {
            this.toolEvents.clear();
        }
    }

    /**
     * 设置当前会话的 Thinking 上下文（线程无关）
     */
    public static void set(Sinks.Many<ServerSentEvent<String>> sink) {
        ThinkingContext ctx = new ThinkingContext(sink, 0);
        activeContext.set(ctx);
        log.info("[ThinkingContextHolder] 设置上下文，线程: {}", ctx.getThreadName());
    }

    /**
     * 获取当前会话的 Thinking 上下文（任意线程可调用）
     */
    public static ThinkingContext get() {
        ThinkingContext ctx = activeContext.get();
        if (ctx != null) {
            log.debug("[ThinkingContextHolder] 获取上下文，设置线程: {}, 当前线程: {}",
                    ctx.getThreadName(), Thread.currentThread().getName());
        } else {
            log.debug("[ThinkingContextHolder] 上下文为 null，当前线程: {}", Thread.currentThread().getName());
        }
        return ctx;
    }

    /**
     * 清除当前会话的 Thinking 上下文
     */
    public static void clear() {
        activeContext.set(null);
        log.info("[ThinkingContextHolder] 清除上下文");
    }
}
