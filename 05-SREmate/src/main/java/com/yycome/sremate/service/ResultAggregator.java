package com.yycome.sremate.service;

import com.yycome.sremate.domain.AggregatedResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结果聚合器
 * 聚合多个工具的执行结果，提取关键信息
 */
@Slf4j
@Service
public class ResultAggregator {

    // 用于提取关键信息的正则模式
    private static final Pattern ERROR_PATTERN = Pattern.compile("(?i)(error|exception|fail|timeout)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?%?\\b");
    private static final Pattern CONNECTION_PATTERN = Pattern.compile("(?i)(connection|connect|连接)");
    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("(?i)(timeout|超时)");

    /**
     * 聚合多个工具结果
     *
     * @param futures 执行结果的Future列表
     * @return 聚合后的结果
     */
    public AggregatedResult aggregate(List<CompletableFuture<Object>> futures) {
        log.info("[AGGREGATOR] 开始聚合 {} 个工具结果", futures.size());

        AggregatedResult aggregated = new AggregatedResult();
        long startTime = System.currentTimeMillis();

        for (CompletableFuture<Object> future : futures) {
            try {
                Object result = future.get();
                if (result instanceof String) {
                    String resultStr = (String) result;

                    // 判断是成功还是失败
                    if (resultStr.startsWith("Error")) {
                        // 提取错误信息
                        String toolName = extractToolName(resultStr);
                        String errorMessage = resultStr;
                        aggregated.addFailedResult(toolName, errorMessage);
                    } else {
                        // 添加成功结果
                        aggregated.addSuccessResult(resultStr);
                    }
                } else {
                    aggregated.addSuccessResult(result);
                }
            } catch (Exception e) {
                log.error("[AGGREGATOR] 获取结果失败: {}", e.getMessage());
                aggregated.addFailedResult("unknown", e.getMessage());
            }
        }

        // 提取关键信息
        aggregated.setKeyInsights(extractKeyInsights(aggregated));

        long duration = System.currentTimeMillis() - startTime;
        aggregated.setTotalDuration(duration);

        log.info("[AGGREGATOR] 聚合完成，成功: {}, 失败: {}, 耗时: {}ms",
                aggregated.getSuccessCount(), aggregated.getFailedCount(), duration);

        return aggregated;
    }

    /**
     * 提取关键信息
     *
     * @param result 聚合结果
     * @return 关键信息列表
     */
    private List<String> extractKeyInsights(AggregatedResult result) {
        List<String> insights = new java.util.ArrayList<>();

        // 从成功结果中提取关键信息
        for (Object successResult : result.getSuccessResults()) {
            if (successResult instanceof String) {
                extractInsightsFromText((String) successResult, insights);
            }
        }

        // 从失败结果中提取关键信息
        for (AggregatedResult.FailedResult failedResult : result.getFailedResults()) {
            insights.add(String.format("工具 %s 执行失败: %s",
                    failedResult.getToolName(), failedResult.getErrorMessage()));
        }

        return insights;
    }

    /**
     * 从文本中提取关键信息
     */
    private void extractInsightsFromText(String text, List<String> insights) {
        // 提取错误信息
        if (ERROR_PATTERN.matcher(text).find()) {
            Matcher matcher = ERROR_PATTERN.matcher(text);
            while (matcher.find()) {
                // 提取错误上下文
                int start = Math.max(0, matcher.start() - 50);
                int end = Math.min(text.length(), matcher.end() + 50);
                String context = text.substring(start, end).trim();
                insights.add("发现错误: " + context);
            }
        }

        // 提取连接相关信息
        if (CONNECTION_PATTERN.matcher(text).find()) {
            insights.add("涉及连接相关问题");
        }

        // 提取超时相关信息
        if (TIMEOUT_PATTERN.matcher(text).find()) {
            insights.add("涉及超时问题");
        }

        // 提取数字指标
        Matcher numberMatcher = NUMBER_PATTERN.matcher(text);
        if (numberMatcher.find()) {
            // 提取前几个数字指标
            int count = 0;
            while (numberMatcher.find() && count < 3) {
                String number = numberMatcher.group();
                insights.add("关键指标: " + number);
                count++;
            }
        }
    }

    /**
     * 从错误信息中提取工具名称
     */
    private String extractToolName(String errorMessage) {
        // 从 "Error executing tool xxx: ..." 格式中提取工具名
        Pattern pattern = Pattern.compile("Error executing tool (\\w+):");
        Matcher matcher = pattern.matcher(errorMessage);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown";
    }

    /**
     * 生成聚合报告
     *
     * @param result 聚合结果
     * @return 报告字符串
     */
    public String generateReport(AggregatedResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 执行结果汇总 ===\n");
        sb.append(String.format("总耗时: %dms%n", result.getTotalDuration()));
        sb.append(String.format("成功: %d, 失败: %d%n%n",
                result.getSuccessCount(), result.getFailedCount()));

        if (!result.getKeyInsights().isEmpty()) {
            sb.append("关键发现:\n");
            for (String insight : result.getKeyInsights()) {
                sb.append("  - ").append(insight).append("\n");
            }
        }

        return sb.toString();
    }
}
