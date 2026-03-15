package com.yycome.sremate.infrastructure.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接输出持有者
 * 用于存储数据查询类工具的结果，绕过 LLM 处理直接输出。
 * 支持收集多个工具结果，在流结束时聚合输出。
 */
@Component
public class DirectOutputHolder {

    /** 单次请求的最终输出（聚合后） */
    private static final AtomicReference<String> OUTPUT = new AtomicReference<>();

    /** 收集所有工具结果（用于聚合） */
    private static final ThreadLocal<List<ToolResult>> RESULTS = ThreadLocal.withInitial(ArrayList::new);

    /**
     * 工具结果记录
     */
    public static class ToolResult {
        public final String toolName;
        public final String result;
        public final long timestamp;

        public ToolResult(String toolName, String result) {
            this.toolName = toolName;
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 添加工具结果（收集模式）
     */
    public void addResult(String toolName, String result) {
        RESULTS.get().add(new ToolResult(toolName, result));
        // 同时设置 OUTPUT 以保持 hasOutput() 兼容
        OUTPUT.set("pending");
    }

    /**
     * 获取所有收集的结果
     */
    public List<ToolResult> getResults() {
        return new ArrayList<>(RESULTS.get());
    }

    /**
     * 设置直接输出内容（覆盖）- 兼容旧逻辑
     */
    public void set(String output) {
        OUTPUT.set(output);
    }

    /**
     * 仅当当前无内容时才写入（first-write-wins）- 兼容旧逻辑
     */
    public boolean setIfAbsent(String output) {
        return OUTPUT.compareAndSet(null, output);
    }

    /**
     * 获取并清除直接输出内容
     */
    public String getAndClear() {
        String result = OUTPUT.getAndSet(null);
        RESULTS.remove();
        return result;
    }

    /**
     * 检查是否有直接输出内容
     */
    public boolean hasOutput() {
        return OUTPUT.get() != null && !RESULTS.get().isEmpty();
    }

    /**
     * 清除直接输出内容
     */
    public void clear() {
        OUTPUT.set(null);
        RESULTS.remove();
    }

    /**
     * 检查是否有多个工具结果需要聚合
     */
    public boolean hasMultipleResults() {
        return RESULTS.get().size() > 1;
    }
}
