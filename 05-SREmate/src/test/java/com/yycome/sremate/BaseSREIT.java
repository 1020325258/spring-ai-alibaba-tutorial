package com.yycome.sremate;

import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 集成测试基类
 * 自动捕获每个测试的输入、输出、耗时、工具调用情况，并写入报告文档。
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseSREIT {

    /** 报告文件路径（相对于模块根目录，即 05-SREmate/） */
    private static final String REPORT_FILE = "docs/test-execution-report.md";

    /** 跨所有类共享：是否已初始化报告文件（写入 header） */
    private static final java.util.concurrent.atomic.AtomicBoolean REPORT_INITIALIZED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Autowired
    protected ChatClient sreAgent;

    @Autowired
    protected TracingService tracingService;

    /** 当前测试类的所有测试记录 */
    private final List<TestRecord> classResults = new ArrayList<>();

    /** 当前测试方法的名称（由 @BeforeEach 设置） */
    private String currentTestName;

    /** 当前测试方法开始时间 */
    private long testStartMs;

    /** 当前测试方法的记录（由 ask() 填充，由 @AfterEach 归档） */
    private TestRecord currentRecord;

    @BeforeEach
    void initTestRecord(TestInfo testInfo) {
        currentTestName = testInfo.getTestMethod()
                .map(Method::getName)
                .orElse("unknown");
        testStartMs = System.currentTimeMillis();
        currentRecord = new TestRecord(getClass().getSimpleName(), currentTestName);
    }

    @AfterEach
    void finalizeTestRecord() {
        if (currentRecord != null) {
            currentRecord.totalDurationMs = System.currentTimeMillis() - testStartMs;
            classResults.add(currentRecord);
        }
    }

    @AfterAll
    void writeClassReport() {
        try {
            Path reportPath = Path.of(REPORT_FILE);
            Files.createDirectories(reportPath.getParent());

            // 第一个类写入时清空文件并写入 header
            if (REPORT_INITIALIZED.compareAndSet(false, true)) {
                String header = "# SREmate 集成测试执行报告\n\n" +
                        "> 最后更新: " + LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n" +
                        "> 运行命令: `./05-SREmate/scripts/run-integration-tests.sh`\n\n" +
                        "---\n\n";
                Files.writeString(reportPath, header,
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            // 追加本测试类的结果
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(getClass().getSimpleName()).append("\n\n");

            for (TestRecord record : classResults) {
                String status = record.failed ? "❌" : "✅";
                sb.append("### ").append(status).append(" ").append(record.testName).append("\n\n");

                if (record.input != null) {
                    sb.append("- **输入:** ").append(record.input).append("\n");
                    sb.append("- **输出:** ").append(truncate(record.output, 300)).append("\n");
                }
                sb.append("- **耗时:** ").append(record.totalDurationMs).append("ms\n");

                if (record.toolCalls.isEmpty()) {
                    sb.append("- **工具调用:** 无\n");
                } else {
                    sb.append("- **工具调用:**\n");
                    for (ToolCall tc : record.toolCalls) {
                        String tcStatus = tc.success ? "✓" : "✗";
                        sb.append("  - `").append(tc.name).append("` ")
                                .append(tcStatus).append(" ").append(tc.durationMs).append("ms\n");
                    }
                }

                if (record.failed && record.failureMessage != null) {
                    sb.append("- **失败原因:** ").append(record.failureMessage).append("\n");
                }

                sb.append("\n");
            }

            sb.append("---\n\n");

            Files.writeString(reportPath, sb.toString(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        } catch (IOException e) {
            System.err.println("[BaseSREIT] 写入测试报告失败: " + e.getMessage());
        }
    }

    /**
     * 向 Agent 发起自然语言请求，自动捕获耗时和工具调用情况。
     * 所有集成测试方法应使用此方法而非直接调用 sreAgent。
     */
    protected String ask(String question) {
        int tracesBefore = tracingService.getTraceCount();
        long start = System.currentTimeMillis();

        String response = sreAgent.prompt()
                .user(question)
                .call()
                .content();

        long duration = System.currentTimeMillis() - start;
        int newTraceCount = tracingService.getTraceCount() - tracesBefore;

        List<ToolCall> toolCalls = captureNewToolCalls(newTraceCount);

        // 填充当前测试记录
        if (currentRecord != null) {
            currentRecord.input = question;
            currentRecord.output = response;
            currentRecord.toolCalls = toolCalls;
        }

        // 控制台结构化输出
        System.out.println("=== 问题: " + question + " ===");
        System.out.println(response);
        String toolSummary = toolCalls.isEmpty() ? "无"
                : toolCalls.stream()
                        .map(t -> t.name + "(" + t.durationMs + "ms" + (t.success ? "" : " FAILED") + ")")
                        .collect(Collectors.joining(", "));
        System.out.printf("⏱ 耗时: %dms | 工具: %s%n%n", duration, toolSummary);

        return response;
    }

    /**
     * 获取最后一次 ask() 调用捕获的工具调用列表
     */
    protected List<ToolCall> getToolCalls() {
        return currentRecord != null ? currentRecord.toolCalls : List.of();
    }

    /**
     * 断言指定工具被调用（且成功）
     */
    protected void assertToolCalled(String toolName) {
        List<ToolCall> calls = getToolCalls();
        boolean found = calls.stream()
                .anyMatch(t -> t.name.equals(toolName) && t.success);
        if (!found) {
            String actualTools = calls.stream()
                    .map(t -> t.name + (t.success ? "" : "(FAILED)"))
                    .collect(Collectors.joining(", "));
            throw new AssertionError(
                    "期望调用工具: " + toolName + ", 实际调用: " + (actualTools.isEmpty() ? "无" : actualTools));
        }
    }

    /**
     * 断言指定工具被调用（包含参数校验）
     */
    protected void assertToolCalled(String toolName, String... expectedParams) {
        // 目前只验证工具名，后续可扩展参数验证
        assertToolCalled(toolName);
    }

    /**
     * 断言指定工具未被调用
     */
    protected void assertToolNotCalled(String toolName) {
        List<ToolCall> calls = getToolCalls();
        boolean found = calls.stream().anyMatch(t -> t.name.equals(toolName));
        if (found) {
            throw new AssertionError("不期望调用工具: " + toolName + ", 但实际被调用了");
        }
    }

    /**
     * 断言工具调用成功（无失败的工具调用）
     */
    protected void assertAllToolsSuccess() {
        List<ToolCall> failed = getToolCalls().stream()
                .filter(t -> !t.success)
                .toList();
        if (!failed.isEmpty()) {
            String failedNames = failed.stream()
                    .map(t -> t.name)
                    .collect(Collectors.joining(", "));
            throw new AssertionError("工具调用失败: " + failedNames);
        }
    }

    /**
     * 断言至少调用了一个工具
     */
    protected void assertAnyToolCalled() {
        if (getToolCalls().isEmpty()) {
            throw new AssertionError("期望至少调用一个工具，但未调用任何工具");
        }
    }

    private List<ToolCall> captureNewToolCalls(int count) {
        List<ToolCall> result = new ArrayList<>();
        int i = 0;
        for (TracingContext ctx : tracingService.getRecentTraces()) {
            if (i >= count) break;
            result.add(new ToolCall(ctx.getToolName(), ctx.getDuration(), ctx.isSuccess()));
            i++;
        }
        return result;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String single = text.replace("\n", " ").replace("\r", "");
        if (single.length() <= maxLen) return single;
        return single.substring(0, maxLen) + "...（截断）";
    }

    // ---- 内部数据结构 ----

    static class TestRecord {
        final String testClass;
        final String testName;
        String input;
        String output;
        long totalDurationMs;
        List<ToolCall> toolCalls = new ArrayList<>();
        boolean failed = false;
        String failureMessage;

        TestRecord(String testClass, String testName) {
            this.testClass = testClass;
            this.testName = testName;
        }
    }

    static class ToolCall {
        final String name;
        final long durationMs;
        final boolean success;

        ToolCall(String name, long durationMs, boolean success) {
            this.name = name;
            this.durationMs = durationMs;
            this.success = success;
        }
    }
}
