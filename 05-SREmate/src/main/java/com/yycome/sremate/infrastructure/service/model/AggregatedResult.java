package com.yycome.sremate.infrastructure.service.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合结果
 * 聚合多个工具执行结果
 */
@Data
public class AggregatedResult {

    /** 成功的结果列表 */
    private List<Object> successResults = new ArrayList<>();

    /** 失败的结果列表 */
    private List<FailedResult> failedResults = new ArrayList<>();

    /** 关键信息提取 */
    private List<String> keyInsights = new ArrayList<>();

    /** 总执行时间（毫秒） */
    private long totalDuration;

    public void addSuccessResult(Object result) {
        successResults.add(result);
    }

    public void addFailedResult(String toolName, String errorMessage) {
        failedResults.add(new FailedResult(toolName, errorMessage));
    }

    public boolean isAllSuccess() {
        return failedResults.isEmpty();
    }

    public int getSuccessCount() {
        return successResults.size();
    }

    public int getFailedCount() {
        return failedResults.size();
    }

    @Data
    public static class FailedResult {
        private final String toolName;
        private final String errorMessage;
    }
}
