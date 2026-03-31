package com.yycome.sreagent.e2e;

import com.yycome.sreagent.config.SREAgentGraphProcess;
import com.yycome.sreagent.infrastructure.service.TracingService;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * SRE-Agent 端到端测试基类
 *
 * <p>封装 Agent 调用和工具追踪，子类只需关注业务场景验证。
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 调用 Agent
 * String response = ask("C1767173898135504的合同基本信息");
 *
 * // 获取工具调用记录
 * List&lt;ToolCall&gt; toolCalls = getToolCalls();
 * </pre>
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseSREAgentIT {

    @Autowired
    protected SREAgentGraphProcess graphProcess;

    @Autowired
    protected TracingService tracingService;

    /** 当前测试方法的记录 */
    private TestRecord currentRecord;

    /** 追踪本次请求前的 trace 数量 */
    private int tracesBefore;

    @BeforeEach
    void initTestRecord(TestInfo testInfo) {
        currentRecord = new TestRecord(
                getClass().getSimpleName(),
                testInfo.getTestMethod().map(m -> m.getName()).orElse("unknown")
        );
    }

    @AfterEach
    void finalizeTestRecord() {
        // 可选：输出测试摘要
    }

    // ========== Agent 调用 ==========

    /**
     * 向 Agent 发起自然语言请求
     */
    protected String ask(String question) {
        tracesBefore = tracingService.getTraceCount();
        long start = System.currentTimeMillis();

        String response = graphProcess.streamAndCollect(question);

        long duration = System.currentTimeMillis() - start;
        int newTraceCount = tracingService.getTraceCount() - tracesBefore;

        List<ToolCall> toolCalls = captureNewToolCalls(newTraceCount);

        currentRecord.input = question;
        currentRecord.output = response;
        currentRecord.toolCalls = toolCalls;
        currentRecord.totalDurationMs = duration;

        // 控制台输出
        System.out.println("=== 问题: " + question + " ===");
        System.out.println(truncate(response, 500));
        String toolSummary = toolCalls.isEmpty() ? "无"
                : toolCalls.stream()
                        .map(t -> t.name + "(" + t.durationMs + "ms" + (t.success ? "" : " FAILED") + ")")
                        .collect(Collectors.joining(", "));
        System.out.printf("⏱ 耗时: %dms | 工具: %s%n%n", duration, toolSummary);

        return response;
    }

    // ========== 工具调用断言 ==========

    /**
     * 断言指定工具被调用（且成功）
     */
    protected void assertToolCalled(String toolName) {
        List<ToolCall> calls = getToolCalls();
        boolean found = calls.stream()
                .anyMatch(c -> c.name.equals(toolName) && c.success);
        if (!found) {
            String actual = calls.stream().map(c -> c.name).collect(Collectors.joining(", "));
            throw new AssertionError(
                    "期望调用工具: " + toolName + ", 实际调用: " + (actual.isEmpty() ? "无" : actual));
        }
    }

    /**
     * 断言所有工具调用都成功
     */
    protected void assertAllToolsSuccess() {
        List<ToolCall> calls = getToolCalls();
        List<String> failed = calls.stream()
                .filter(c -> !c.success)
                .map(c -> c.name)
                .toList();
        if (!failed.isEmpty()) {
            throw new AssertionError("以下工具调用失败: " + String.join(", ", failed));
        }
    }

    /**
     * 获取指定工具的调用参数（取最后一次调用）
     */
    protected Map<String, Object> getToolParams(String toolName) {
        return getToolCalls().stream()
                .filter(c -> c.name.equals(toolName))
                .reduce((first, second) -> second)
                .map(c -> c.params)
                .orElse(Map.of());
    }

    /**
     * 断言工具调用参数中包含指定键值
     */
    protected void assertToolParamEquals(String toolName, String key, Object value) {
        Map<String, Object> params = getToolParams(toolName);
        if (!params.containsKey(key)) {
            throw new AssertionError("工具 " + toolName + " 调用参数中缺少: " + key +
                    ", 实际参数: " + params);
        }
        Object actual = params.get(key);
        if (!value.equals(actual)) {
            throw new AssertionError("工具 " + toolName + " 参数 " + key +
                    " 期望: " + value + ", 实际: " + actual);
        }
    }

    // ========== 辅助方法 ==========

    protected List<ToolCall> getToolCalls() {
        return currentRecord != null ? currentRecord.toolCalls : List.of();
    }

    protected String getOutput() {
        return currentRecord != null ? currentRecord.output : "";
    }

    private List<ToolCall> captureNewToolCalls(int count) {
        ConcurrentLinkedDeque<TracingContext> allContexts = tracingService.getRecentTraces();
        List<ToolCall> result = new ArrayList<>();
        int i = 0;
        for (TracingContext ctx : allContexts) {
            if (i >= count) break;
            result.add(new ToolCall(
                    ctx.getToolName(),
                    ctx.getDuration(),
                    ctx.isSuccess(),
                    ctx.getParams()
            ));
            i++;
        }
        return result;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ========== 内部记录类 ==========

    static class TestRecord {
        String className;
        String testName;
        String input;
        String output;
        long totalDurationMs;
        List<ToolCall> toolCalls = new ArrayList<>();

        TestRecord(String className, String testName) {
            this.className = className;
            this.testName = testName;
        }
    }

    record ToolCall(String name, long durationMs, boolean success, Map<String, Object> params) {}
}
