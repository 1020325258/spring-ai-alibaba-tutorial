package com.yycome.sreagent.e2e;

import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.yycome.sreagent.config.SREAgentGraph;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.infrastructure.service.TracingService;
import com.yycome.sreagent.infrastructure.service.model.TracingContext;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

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
import java.util.stream.Collectors;

/**
 * SRE-Agent 端到端测试基类
 * 自动捕获每个测试的输入、输出、耗时、工具调用情况，并写入报告文档。
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseSREAgentIT {

    /** 报告文件路径（相对于模块根目录，即 06-SRE-Agent/） */
    private static final String REPORT_FILE = "docs/test-execution-report.md";

    /** 跨所有类共享：是否已初始化报告文件（写入 header） */
    private static final java.util.concurrent.atomic.AtomicBoolean REPORT_INITIALIZED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Autowired
    private SREAgentGraph sreAgent;

    /** 无工具绑定的底层模型，专用于 LLM-as-Judge 评估，避免干扰 TracingService */
    @Autowired
    private ChatModel chatModel;

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
                String header = "# SRE-Agent 端到端测试执行报告\n\n" +
                        "> 最后更新: " + LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n" +
                        "> 运行命令: `./scripts/run-integration-tests.sh`\n\n" +
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
            System.err.println("[BaseSREAgentIT] 写入测试报告失败: " + e.getMessage());
        }
    }

    /**
     * 向 Agent 发起自然语言请求，自动捕获耗时和工具调用情况。
     * 所有端到端测试方法应使用此方法而非直接调用 sreAgent。
     */
    protected String ask(String question) {
        int tracesBefore = tracingService.getTraceCount();

        // 耗时分析
        AtomicLong ttfbMs = new AtomicLong(-1);
        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);

        long start = System.currentTimeMillis();

        try {
            sreAgent.streamMessages(question)
                    .filter(msg -> msg instanceof AssistantMessage am && StringUtils.hasText(am.getText()))
                    .map(msg -> ((AssistantMessage) msg).getText())
                    .doOnNext(chunk -> {
                        if (ttfbMs.get() < 0) {
                            ttfbMs.set(System.currentTimeMillis() - start);
                        }
                        responseBuilder.append(chunk);
                    })
                    .doOnComplete(latch::countDown)
                    .doOnError(e -> latch.countDown())
                    .subscribe();
        } catch (GraphRunnerException e) {
            latch.countDown();
            throw new RuntimeException("sreAgent.streamMessages 失败", e);
        }

        try {
            latch.await(120, java.util.concurrent.TimeUnit.SECONDS);
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
     */
    protected Map<String, Object> getToolParams(String toolName) {
        List<ToolCall> calls = getToolCalls();
        return calls.stream()
                .filter(c -> c.name.equals(toolName))
                .reduce((first, second) -> second)  // 取最后一个
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

    // ── 输出内容验证 ────────────────────────────────────────────

    private static final ObjectMapper OUTPUT_MAPPER = new ObjectMapper();

    /**
     * 将最后一次 ask() 的输出解析为 JsonNode。
     * 支持以下输出格式（兼容 streamMessages 的 agent 标签和 JSON 代码块包裹）：
     * - 纯 JSON：{...}
     * - 带标签：**[queryAgent]**\n\n{...}  或  **[queryAgent]**\n\n```json\n{...}\n```
     * - 带路由器前缀：> **[路由器]** ...\n\n**[queryAgent]**\n\n{...}
     */
    protected JsonNode parseOutput() {
        String output = currentRecord != null ? currentRecord.output : null;
        Assertions.assertThat(output)
                .as("ask() 的输出不能为空")
                .isNotBlank();
        try {
            return OUTPUT_MAPPER.readTree(extractJsonPart(output));
        } catch (Exception e) {
            throw new AssertionError("输出不是合法 JSON: " + truncate(output, 200) + "\n原因: " + e.getMessage());
        }
    }

    /**
     * 从可能带有 agent 标签或 ```json 代码块的输出中提取纯 JSON 字符串。
     */
    private String extractJsonPart(String output) {
        // 优先从 ```json ... ``` 代码块中提取
        int codeBlockStart = output.indexOf("```json");
        if (codeBlockStart >= 0) {
            int contentStart = codeBlockStart + "```json".length();
            int codeBlockEnd = output.lastIndexOf("```");
            if (codeBlockEnd > contentStart) {
                return output.substring(contentStart, codeBlockEnd).trim();
            }
        }
        // 退化：找到第一个 { 和最后一个 } 之间的内容
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return output.substring(start, end + 1);
        }
        return output;
    }

    /**
     * 断言输出 JSON 的顶层字段值等于期望值。
     */
    protected void assertOutputField(String fieldPath, String expectedValue) {
        JsonNode root = parseOutput();
        JsonNode node = root.at("/" + fieldPath.replace(".", "/"));
        Assertions.assertThat(node.isMissingNode())
                .as("输出 JSON 中缺少字段: " + fieldPath)
                .isFalse();
        Assertions.assertThat(node.asText())
                .as("输出字段 [" + fieldPath + "] 期望: " + expectedValue)
                .isEqualTo(expectedValue);
    }

    /**
     * 断言输出的 records 数组不为空
     */
    protected void assertOutputHasRecords() {
        JsonNode root = parseOutput();
        JsonNode records = root.path("records");
        Assertions.assertThat(records.isArray())
                .as("输出 JSON 中 records 应为数组")
                .isTrue();
        Assertions.assertThat(records.size())
                .as("records 不应为空")
                .isGreaterThan(0);
    }

    /**
     * 断言 records[0] 中存在指定字段
     */
    protected void assertFirstRecordHasField(String fieldPath) {
        JsonNode root = parseOutput();
        JsonNode firstRecord = root.path("records").path(0);
        Assertions.assertThat(firstRecord.isMissingNode())
                .as("records[0] 不存在")
                .isFalse();
        JsonNode field = firstRecord.at("/" + fieldPath.replace(".", "/"));
        Assertions.assertThat(field.isMissingNode() || field.isNull())
                .as("records[0] 中缺少字段: " + fieldPath)
                .isFalse();
    }

    /**
     * 断言输出包含特定关键词（用于非 JSON 输出场景）
     */
    protected void assertOutputContains(String keyword) {
        String output = currentRecord != null ? currentRecord.output : "";
        Assertions.assertThat(output)
                .as("输出应包含关键词: " + keyword)
                .contains(keyword);
    }

    /**
     * LLM-as-Judge：断言输出是一份真实的排查结论（自然语言分析+结论），而非原始 JSON 数据。
     *
     * <p>使用无工具绑定的 {@link ChatModel} 发起评估调用，避免干扰 TracingService 工具调用计数。
     * 当 judge 返回 {"pass": false} 时抛出 AssertionError。
     * 当 judge 返回内容无法解析为 JSON 时，降级为检查响应不以 "{" 或 "[" 开头。
     */
    private static final String JUDGE_PROMPT_TEMPLATE = """
            你是一个输出格式评估专家。请判断以下内容是否符合"排查结论"的标准。

            【排查结论的判定标准（必须同时满足）】
            1. 主体内容是自然语言（不是原始 JSON 数据）
            2. 包含对数据的分析或解读（不只是列出数据，要有分析性语言）
            3. 包含至少一个明确的判断、结论或建议

            【重要说明】
            - 如果输出的主要内容是 {...} JSON，即使开头有一句话，也判定为失败
            - 输出可以引用数据中的字段名或值，但必须有分析性语言

            【待评估内容】
            %s

            【只输出以下 JSON，不要其他文字】
            {"pass": true, "reason": "理由（20字以内）"}
            """;

    protected void assertOutputIsInvestigationConclusion(String response) {
        String judgePrompt = JUDGE_PROMPT_TEMPLATE.formatted(truncate(response, 2000));

        String judgment;
        try {
            judgment = chatModel.call(new Prompt(judgePrompt))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            // judge 调用失败时降级为简单断言
            Assertions.assertThat(response)
                    .as("输出不应为原始 JSON（judge 调用失败，已降级）\n实际输出前200字: " + truncate(response, 200))
                    .doesNotStartWith("{")
                    .doesNotStartWith("[");
            return;
        }

        try {
            JsonNode result = OUTPUT_MAPPER.readTree(judgment.trim());
            boolean pass = result.path("pass").asBoolean();
            String reason = result.path("reason").asText("无理由");
            Assertions.assertThat(pass)
                    .as("输出不符合排查结论标准 → " + reason
                            + "\n实际输出前200字: " + truncate(response, 200))
                    .isTrue();
        } catch (Exception e) {
            // judge 返回内容无法解析为 JSON 时降级为简单断言
            Assertions.assertThat(response)
                    .as("输出不应为原始 JSON（judge 解析失败，已降级）\n实际输出前200字: " + truncate(response, 200))
                    .doesNotStartWith("{")
                    .doesNotStartWith("[");
        }
    }

    private static final String SKILL_COMPLIANCE_JUDGE_PROMPT = """
            你是一个 SRE Agent 执行合规性评估专家。请判断以下执行过程是否符合技能指南的核心要求。

            【技能名称】
            %s

            【技能指南（SKILL.md）】
            %s

            【实际执行的工具调用顺序（时间正序）】
            %s

            【Agent 最终输出（前800字）】
            %s

            【评估要点（仅以下三点，条件分支不作为违规依据）】
            1. readSkill 必须是第一个被调用的工具（在任何 ontologyQuery 之前）
            2. 技能指南第一步要求的 ontologyQuery（entity 和 queryScope）必须被调用
            3. 输出结论必须是自然语言四段式（数据查询/分析/结论/建议），不能是裸 JSON

            【重要说明】
            - Agent 可能出于效率对多步查询并行或提前执行，只要核心查询都做了，条件分支偏差不算违规
            - 不要对"是否根据中间结果决策跳过后续步骤"进行评判，这超出了静态合规检查的范围

            【只输出以下 JSON，不要其他文字】
            {"pass": true, "reason": "理由（30字以内）", "violations": []}
            """;

    /**
     * LLM-as-Judge：验证 Agent 执行过程是否符合指定 Skill 的步骤要求。
     * 读取技能的 SKILL.md，将工具调用序列和最终输出一起交给 LLM 评估合规性。
     */
    protected void assertSkillProcessCompliance(String skillName, String response) {
        String skillContent;
        try {
            skillContent = new ClassPathResource("skills/" + skillName + "/SKILL.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("无法读取技能文件: skills/" + skillName + "/SKILL.md → " + e.getMessage());
        }

        // TracingService 的 deque 以"最新在前"顺序存储，需反转为时间顺序
        List<ToolCall> calls = new ArrayList<>(getToolCalls());
        java.util.Collections.reverse(calls);
        String toolSequence = calls.isEmpty() ? "（无工具调用）"
                : calls.stream()
                        .map(c -> c.name + "(" + c.params.entrySet().stream()
                                .map(e -> e.getKey() + "=" + e.getValue())
                                .collect(Collectors.joining(", ")) + ")")
                        .collect(Collectors.joining(" → "));

        String judgePrompt = SKILL_COMPLIANCE_JUDGE_PROMPT.formatted(
                skillName, skillContent, toolSequence, truncate(response, 800));

        String judgment;
        try {
            judgment = chatModel.call(new Prompt(judgePrompt))
                    .getResult()
                    .getOutput()
                    .getText();
        } catch (Exception e) {
            Assertions.assertThat(toolSequence)
                    .as("Skill 合规性 judge 调用失败（降级模式）\n工具调用序列: " + toolSequence)
                    .contains("readSkill");
            return;
        }

        try {
            JsonNode result = OUTPUT_MAPPER.readTree(judgment.trim());
            boolean pass = result.path("pass").asBoolean();
            String reason = result.path("reason").asText("无理由");
            JsonNode violations = result.path("violations");
            String violationList = violations.isArray() && violations.size() > 0
                    ? violations.toString() : "无";
            Assertions.assertThat(pass)
                    .as("执行过程不符合 Skill [" + skillName + "] 要求\n"
                            + "原因: " + reason + "\n"
                            + "违规点: " + violationList + "\n"
                            + "工具调用序列: " + toolSequence)
                    .isTrue();
        } catch (Exception e) {
            Assertions.assertThat(toolSequence)
                    .as("Skill 合规性判断失败（降级模式），工具调用序列: " + toolSequence)
                    .contains("readSkill");
        }
    }

    /**
     * 从 TracingService 中捕获最近的工具调用记录
     */
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