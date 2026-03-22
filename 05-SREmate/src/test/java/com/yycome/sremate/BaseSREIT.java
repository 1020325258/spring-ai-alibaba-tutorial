package com.yycome.sremate;

import com.yycome.sremate.infrastructure.service.DirectOutputHolder;
import com.yycome.sremate.infrastructure.service.TracingService;
import com.yycome.sremate.infrastructure.service.model.TracingContext;
import org.assertj.core.api.Assertions;
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
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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

    @Autowired
    protected DirectOutputHolder directOutputHolder;

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
     *
     * 支持 DirectOutput 机制：如果工具标记为 @DataQueryTool，
     * 结果直接返回，绕过 LLM 处理。
     *
     * 耗时分析：
     * - ttfb: 首字节时间（LLM 响应开始）
     * - toolTime: 工具执行时间
     * - totalTime: 总耗时
     */
    protected String ask(String question) {
        int tracesBefore = tracingService.getTraceCount();

        // 开始新请求（生成请求ID，支持跨线程结果收集）
        directOutputHolder.startRequest();

        // 耗时分析
        AtomicLong ttfbMs = new AtomicLong(-1);
        AtomicBoolean directOutputUsed = new AtomicBoolean(false);
        StringBuilder responseBuilder = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<reactor.core.Disposable> subscription = new java.util.concurrent.atomic.AtomicReference<>();

        long start = System.currentTimeMillis();

        // 使用流式调用，支持 DirectOutput 旁路
        reactor.core.Disposable sub = sreAgent.prompt()
                .user(question)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    // 记录首字节时间
                    if (ttfbMs.get() < 0) {
                        ttfbMs.set(System.currentTimeMillis() - start);

                        // 首字节到达时，检查是否有直接输出
                        if (directOutputHolder.hasOutput() && !directOutputUsed.get()) {
                            directOutputUsed.set(true);
                            String directOutput = directOutputHolder.getAndClearAggregated();
                            responseBuilder.append(directOutput);
                            // 关键：立即中断流，不再等待 LLM 完成剩余输出
                            reactor.core.Disposable current = subscription.get();
                            if (current != null) current.dispose();
                            return;
                        }
                    }

                    // 已走直接输出路径，忽略后续 LLM token
                    if (directOutputUsed.get()) return;

                    responseBuilder.append(chunk);
                })
                .doOnComplete(latch::countDown)
                .doOnError(e -> latch.countDown())
                .doOnCancel(latch::countDown)
                .subscribe();
        subscription.set(sub);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long totalMs = System.currentTimeMillis() - start;
        int newTraceCount = tracingService.getTraceCount() - tracesBefore;

        String response = responseBuilder.toString();

        List<ToolCall> toolCalls = captureNewToolCalls(newTraceCount);

        // 计算工具总耗时
        long toolTotalMs = toolCalls.stream().mapToLong(t -> t.durationMs).sum();

        // 填充当前测试记录
        if (currentRecord != null) {
            currentRecord.input = question;
            currentRecord.output = response;
            currentRecord.toolCalls = toolCalls;
        }

        // 控制台结构化输出（含耗时分析）
        System.out.println("=== 问题: " + question + " ===");
        System.out.println(response);
        String toolSummary = toolCalls.isEmpty() ? "无"
                : toolCalls.stream()
                        .map(t -> t.name + "(" + t.durationMs + "ms" + (t.success ? "" : " FAILED") + ")")
                        .collect(Collectors.joining(", "));

        // 耗时分析日志
        System.out.printf("⏱ 首字节: %dms | 工具耗时: %dms | 总耗时: %dms | 工具: %s%n%n",
                ttfbMs.get(), toolTotalMs, totalMs, toolSummary);

        // 如果 DirectOutput 生效，会有明显的时间差异
        if (directOutputUsed.get()) {
            System.out.println("[DirectOutput] ✓ 已生效，绕过 LLM 处理");
        }

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
                .anyMatch(c -> c.name.equals(toolName) && c.success);
        if (!found) {
            String actual = calls.stream()
                    .map(c -> c.name)
                    .collect(Collectors.joining(", "));
            throw new AssertionError(
                    "期望调用工具: " + toolName + ", 实际调用: " + (actual.isEmpty() ? "无" : actual));
        }
    }

    /**
     * 断言指定工具未被调用
     */
    protected void assertToolNotCalled(String toolName) {
        List<ToolCall> calls = getToolCalls();
        boolean found = calls.stream()
                .anyMatch(c -> c.name.equals(toolName));
        if (found) {
            throw new AssertionError("不期望工具被调用: " + toolName);
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
     *
     * @param toolName 工具名称
     * @return 工具调用参数，如果工具未被调用则返回空 Map
     */
    protected Map<String, Object> getToolParams(String toolName) {
        List<ToolCall> calls = getToolCalls();
        // 使用 reduce 获取最后一个匹配的调用
        return calls.stream()
                .filter(c -> c.name.equals(toolName))
                .reduce((first, second) -> second)  // 取最后一个
                .map(c -> c.params)
                .orElse(Map.of());
    }

    /**
     * 断言工具调用参数中包含指定键值
     *
     * @param toolName 工具名称
     * @param key      参数名
     * @param value    期望值
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

    /**
     * 断言工具调用参数中包含指定键值（忽略逗号分隔列表的顺序）
     *
     * @param toolName 工具名称
     * @param key      参数名
     * @param expectedValue 期望值（逗号分隔的列表）
     */
    protected void assertToolParamEqualsIgnoreOrder(String toolName, String key, String expectedValue) {
        Map<String, Object> params = getToolParams(toolName);
        if (!params.containsKey(key)) {
            throw new AssertionError("工具 " + toolName + " 调用参数中缺少: " + key +
                    ", 实际参数: " + params);
        }
        String actual = params.get(key).toString();
        // 分割并排序后比较
        String[] expectedParts = expectedValue.split(",");
        String[] actualParts = actual.split(",");
        java.util.Arrays.sort(expectedParts);
        java.util.Arrays.sort(actualParts);
        if (!java.util.Arrays.equals(expectedParts, actualParts)) {
            throw new AssertionError("工具 " + toolName + " 参数 " + key +
                    " 期望: " + expectedValue + ", 实际: " + actual);
        }
    }

    /**
     * 断言 ontologyQuery 工具的参数正确性
     *
     * v2.4 更新：工具不再自动修正 entity，信任 LLM 的判断
     * 因此这里直接验证 LLM 传入的 entity 参数
     *
     * @param entity     期望的实体类型
     * @param queryScope 期望的查询范围 (可为 null 表示不验证)
     */
    protected void assertOntologyQueryParams(String entity, String queryScope) {
        assertToolCalled("ontologyQuery");
        Map<String, Object> params = getToolParams("ontologyQuery");

        // 直接验证 entity 参数（信任 LLM 的判断）
        Object entityObj = params.get("entity");
        Assertions.assertThat(entityObj)
                .as("entity 参数应该为: " + entity)
                .isEqualTo(entity);

        if (queryScope != null) {
            // 如果 queryScope 包含逗号，使用忽略顺序的比较
            if (queryScope.contains(",")) {
                assertToolParamEqualsIgnoreOrder("ontologyQuery", "queryScope", queryScope);
            } else {
                assertToolParamEquals("ontologyQuery", "queryScope", queryScope);
            }
        }
    }

    /**
     * 根据 value 格式推断正确的 entity 类型
     * @deprecated v2.4 后工具不再自动修正 entity，此方法保留用于向后兼容
     */
    @Deprecated
    private String inferEntityFromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        // C开头 → Contract
        if (trimmed.toUpperCase().startsWith("C")) {
            return "Contract";
        }
        // 纯数字 → Order
        if (trimmed.matches("\\d+")) {
            return "Order";
        }
        return null;
    }

    /**
     * 从 TracingService 中捕获最近的工具调用记录
     * 注意：ConcurrentLinkedDeque 头部是最新的记录
     */
    private List<ToolCall> captureNewToolCalls(int count) {
        ConcurrentLinkedDeque<TracingContext> allContexts = tracingService.getRecentTraces();
        // 取队列头部的 count 个元素（最新添加的）
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

    // --- 内部记录类 ---

    static class TestRecord {
        String className;
        String testName;
        String input;
        String output;
        long totalDurationMs;
        List<ToolCall> toolCalls = new ArrayList<>();
        boolean failed;
        String failureMessage;

        TestRecord(String className, String testName) {
            this.className = className;
            this.testName = testName;
        }
    }

    static class ToolCall {
        String name;
        long durationMs;
        boolean success;
        Map<String, Object> params;

        ToolCall(String name, long durationMs, boolean success, Map<String, Object> params) {
            this.name = name;
            this.durationMs = durationMs;
            this.success = success;
            this.params = params != null ? params : Map.of();
        }
    }
}
