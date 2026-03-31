package com.yycome.sreagent.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * QA 评估报告生成器
 * 测试完成后生成 Markdown 格式的完整报告
 */
public class QaEvaluationReporter {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path outputPath;

    public QaEvaluationReporter(Path outputPath) {
        this.outputPath = outputPath;
    }

    /**
     * 生成评估报告
     *
     * @param results 所有 QA pair 的评估结果
     */
    public void generate(List<QaEvaluationResult> results) {
        String report = buildReport(results);
        writeReport(report);
    }

    private String buildReport(List<QaEvaluationResult> results) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# SRE-Agent QA 评估报告\n\n");
        sb.append("> 执行时间: ").append(LocalDateTime.now().format(TIMESTAMP_FORMATTER)).append("\n\n");

        // Statistics
        sb.append("## 统计\n\n");
        long passCount = results.stream().filter(r -> r.judgeResult().pass()).count();
        long failCount = results.size() - passCount;
        sb.append("| 状态 | 数量 |\n");
        sb.append("|------|------|\n");
        sb.append("| ✅ 通过 | ").append(passCount).append(" |\n");
        sb.append("| ❌ 失败 | ").append(failCount).append(" |\n\n");
        sb.append("---\n\n");

        // Detailed results
        sb.append("## 详细结果\n\n");
        for (QaEvaluationResult result : results) {
            sb.append(buildResultSection(result));
        }

        return sb.toString();
    }

    private String buildResultSection(QaEvaluationResult result) {
        StringBuilder sb = new StringBuilder();

        String statusIcon = result.judgeResult().pass() ? "✅" : "❌";
        String statusText = result.judgeResult().pass() ? "通过" : "失败";

        sb.append("### ").append(statusIcon).append(" ").append(result.qaPair().id()).append("\n\n");

        // 完整输入
        sb.append("**输入:**\n```\n").append(result.qaPair().question()).append("\n```\n\n");

        // 完整预期
        sb.append("**预期:**\n```\n").append(result.qaPair().expected()).append("\n```\n\n");

        // 完整实际输出
        sb.append("**实际输出:**\n```\n").append(result.actualOutput()).append("\n```\n\n");

        // 评估结果和理由
        sb.append("**评估结果:** ").append(statusIcon).append(" ").append(statusText).append("\n\n");
        sb.append("**评估理由:**\n").append(result.judgeResult().reason()).append("\n\n");
        sb.append("---\n\n");

        return sb.toString();
    }

    private void writeReport(String content) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("无法写入报告文件: " + outputPath, e);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * 单个 QA pair 的评估结果
     */
    public record QaEvaluationResult(
            QaPair qaPair,
            String actualOutput,
            EvaluationJudge.JudgeResult judgeResult
    ) {}
}
