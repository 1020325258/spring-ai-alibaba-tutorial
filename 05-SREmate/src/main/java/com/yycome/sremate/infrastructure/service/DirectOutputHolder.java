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
     * 设置直接输出内容（覆盖）
     */
    public void set(String output) {
        OUTPUT.set(output);
    }

    /**
     * 仅当当前无内容时才写入（first-write-wins）。
     * 防止同一请求中多个 DATA_QUERY 工具相互覆盖，保证第一个写入的结果被使用。
     *
     * @return true 表示写入成功；false 表示已有内容，本次忽略
     */
    public boolean setIfAbsent(String output) {
        return OUTPUT.compareAndSet(null, output);
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
