package com.yycome.sremate.infrastructure.service;

import org.springframework.stereotype.Component;

/**
 * 直接输出持有者（ThreadLocal）
 * 用于存储数据查询类工具的结果，绕过 LLM 处理直接输出
 */
@Component
public class DirectOutputHolder {

    private static final ThreadLocal<String> OUTPUT = new ThreadLocal<>();

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
        String result = OUTPUT.get();
        OUTPUT.remove();
        return result;
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
        OUTPUT.remove();
    }
}
