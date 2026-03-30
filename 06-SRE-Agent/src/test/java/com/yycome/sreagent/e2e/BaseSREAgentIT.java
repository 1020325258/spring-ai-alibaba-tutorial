package com.yycome.sreagent.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRE-Agent 端到端测试基类
 *
 * <p>封装 Agent 调用、工具追踪和断言逻辑，子类只需关注业务场景验证。
 *
 * <h3>使用方式</h3>
 * <pre>
 * // 1. 调用 Agent
 * String response = ask("C1767173898135504的合同基本信息");
 *
 * // 2. 验证工具调用
 * assertToolCalled("ontologyQuery");
 * assertToolParamEquals("ontologyQuery", "entity", "Contract");
 *
 * // 3. 验证输出内容
 * assertOutputField("queryEntity", "Contract");
 * assertOutputHasRecords();
 * </pre>
 *
 * <h3>QaPair 验证</h3>
 * <pre>
 * assertQaPairCompliance(qaPair);  // 自动分发到三种验证类型
 * </pre>
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseSREAgentIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    // ========== QaPair 验证入口 ==========

    /**
     * 验证 QaPair 合规性
     * 根据类型自动分发到对应的验证方法
     */
    protected void assertQaPairCompliance(QaPair qa) {
        String response = ask(qa.question());

        // 验证主期望
        assertExpected(qa.expected(), response, qa.id());

        // 验证附加期望
        if (qa.also() != null) {
            assertExpected(qa.also(), response, qa.id() + " (also)");
        }
    }

    private void assertExpected(QaPair.Expected expected, String response, String context) {
        if (expected == null) return;

        if (expected.isToolCallType()) {
            assertToolCallExpected(expected, context);
        } else if (expected.isJsonOutputType()) {
            assertJsonOutputExpected(expected, response, context);
        } else if (expected.isNaturalLanguageType()) {
            assertNaturalLanguageExpected(expected, response, context);
        }
    }

    // ========== 三种验证类型实现 ==========

    /**
     * 验证 tool_call 类型：工具被调用且参数匹配
     */
    private void assertToolCallExpected(QaPair.Expected expected, String context) {
        String tool = expected.tool();
        assertToolCalled(tool);

        if (expected.params() != null) {
            for (Map.Entry<String, Object> entry : expected.params().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                // null 值表示参数不应存在或为 null
                if (value == null) {
                    Map<String, Object> params = getToolParams(tool);
                    assertThat(params.containsKey(key) && params.get(key) != null)
                            .as("[%s] 工具 %s 参数 %s 应为 null 或不存在", context, tool, key)
                            .isFalse();
                } else {
                    assertToolParamEquals(tool, key, value);
                }
            }
        }
    }

    /**
     * 验证 json_output 类型：输出 JSON 包含指定字段
     */
    private void assertJsonOutputExpected(QaPair.Expected expected, String response, String context) {
        try {
            String json = extractJson(response);
            JsonNode root = MAPPER.readTree(json);

            if (expected.queryEntity() != null) {
                assertThat(root.has("queryEntity"))
                        .as("[%s] 输出应包含 queryEntity 字段", context)
                        .isTrue();
                assertThat(root.get("queryEntity").asText())
                        .as("[%s] queryEntity 应为 %s", context, expected.queryEntity())
                        .isEqualTo(expected.queryEntity());
            }
        } catch (Exception e) {
            throw new AssertionError("[%s] JSON 解析失败: %s".formatted(context, e.getMessage()));
        }
    }

    /**
     * 验证 natural_language 类型：输出包含关键词
     */
    private void assertNaturalLanguageExpected(QaPair.Expected expected, String response, String context) {
        if (expected.mustContain() != null) {
            for (String keyword : expected.mustContain()) {
                assertThat(response.contains(keyword))
                        .as("[%s] 输出应包含关键词 '%s'", context, keyword)
                        .isTrue();
            }
        }
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

    // ========== 输出内容断言 ==========

    /**
     * 断言输出 JSON 的顶层字段值
     */
    protected void assertOutputField(String fieldPath, String expectedValue) {
        JsonNode root = parseOutput();
        JsonNode node = root.at("/" + fieldPath.replace(".", "/"));
        assertThat(node.isMissingNode())
                .as("输出 JSON 中缺少字段: " + fieldPath)
                .isFalse();
        assertThat(node.asText())
                .as("输出字段 [" + fieldPath + "] 期望: " + expectedValue)
                .isEqualTo(expectedValue);
    }

    /**
     * 断言输出的 records 数组不为空
     */
    protected void assertOutputHasRecords() {
        JsonNode root = parseOutput();
        JsonNode records = root.path("records");
        assertThat(records.isArray())
                .as("输出 JSON 中 records 应为数组")
                .isTrue();
        assertThat(records.size())
                .as("records 不应为空")
                .isGreaterThan(0);
    }

    // ========== 辅助方法 ==========

    protected List<ToolCall> getToolCalls() {
        return currentRecord != null ? currentRecord.toolCalls : List.of();
    }

    protected String getOutput() {
        return currentRecord != null ? currentRecord.output : "";
    }

    private JsonNode parseOutput() {
        String output = getOutput();
        assertThat(output).as("ask() 的输出不能为空").isNotBlank();
        try {
            String json = extractJson(output);
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("输出不是合法 JSON: " + truncate(output, 200));
        }
    }

    private String extractJson(String text) {
        if (text == null || text.isEmpty()) return text;
        // 去除 markdown 代码块
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
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
