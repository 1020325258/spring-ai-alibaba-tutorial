package com.yycome.sremate.infrastructure.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接输出持有者
 * 用于存储数据查询类工具的结果，绕过 LLM 处理直接输出。
 * 使用 AtomicReference 确保跨线程可见（Reactor doOnNext 与工具调用线程不同）。
 */
@Component
public class DirectOutputHolder {

    private static final AtomicReference<String> OUTPUT = new AtomicReference<>();

    /**
     * 设置直接输出内容
     */
    public void set(String output) {
        OUTPUT.set(output);
    }

    /**
     * 获取并清除直接输出内容
     */
    public String getAndClear() {
        return OUTPUT.getAndSet(null);
    }

    /**
     * 检查是否有直接输出内容
     */
    public boolean hasOutput() {
        return OUTPUT.get() != null;
    }

    /**
     * 清除直接输出内容
     */
    public void clear() {
        OUTPUT.set(null);
    }
}
