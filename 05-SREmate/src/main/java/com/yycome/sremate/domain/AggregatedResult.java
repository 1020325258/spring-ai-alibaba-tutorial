package com.yycome.sremate.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合结果
 * 聚合多个工具执行结果
 */
@Data
public class AggregatedResult {

    /**
     * 成功的结果列表
     */
    private List<Object> successResults = new ArrayList<>();

    /**
     * 失败的结果列表
     */
    private List<FailedResult> failedResults = new ArrayList<>();

    /**
     * 关键信息提取
     */
    private List<String> keyInsights = new ArrayList<>();

    /**
     * 总执行时间（毫秒）
     */
    private long totalDuration;

    /**
     * 添加成功结果
     */
    public void addSuccessResult(Object result) {
        successResults.add(result);
    }

    /**
     * 添加失败结果
     */
    public void addFailedResult(String toolName, String errorMessage) {
        failedResults.add(new FailedResult(toolName, errorMessage));
    }

    /**
     * 是否全部成功
     */
    public boolean isAllSuccess() {
        return failedResults.isEmpty();
    }

    /**
     * 获取成功数量
     */
    public int getSuccessCount() {
        return successResults.size();
    }

    /**
     * 获取失败数量
     */
    public int getFailedCount() {
        return failedResults.size();
    }

    /**
     * 失败结果
     */
    @Data
    public static class FailedResult {
        private final String toolName;
        private final String errorMessage;
    }
}
