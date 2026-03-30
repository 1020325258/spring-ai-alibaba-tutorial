package com.yycome.sreagent.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Agent 评估测试
 *
 * 使用预定义的问答对（YAML 文件）评估各 Agent 的输出质量。
 *
 * 问答对文件格式（testcases/xxx.yaml）：
 * <pre>
 * agent: adminAgent
 * cases:
 *   - name: 查询本体实体列表
 *     input: 本体模型有哪些
 *     expected:
 *       - type: contains
 *         value: Order
 *       - type: contains
 *         value: Contract
 *   - name: 查询环境列表
 *     input: 当前环境是什么
 *     expected:
 *       - type: contains
 *         value: nrs-escrow
 * </pre>
 *
 * expected 支持的断言类型：
 * - contains: 响应包含指定字符串
 * - not_contains: 响应不包含指定字符串
 * - matches: 响应匹配正则表达式
 * - is_json: 响应为有效 JSON
 *
 * 运行方式：
 * <pre>
 * mvn test -Dtest=AgentEvaluationIT
 * mvn test -Dtest=AgentEvaluationIT -Dagent=testcases/adminAgent.yaml
 * mvn test -Dtest=AgentEvaluationIT -Dagent=all
 * </pre>
 */
@Slf4j
public class AgentEvaluationIT {

    private static final Path TESTCASES_DIR = Path.of("src/test/resources/testcases");
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private static List<TestCaseFile> testCaseFiles;

    @BeforeAll
    static void loadTestCases() throws IOException {
        testCaseFiles = Files.list(TESTCASES_DIR)
                .filter(p -> p.toString().endsWith(".yaml"))
                .map(path -> {
                    try {
                        return yamlMapper.readValue(path.toFile(), TestCaseFile.class);
                    } catch (IOException e) {
                        log.error("加载测试用例失败: {}", path, e);
                        return null;
                    }
                })
                .filter(f -> f != null)
                .toList();
        log.info("加载了 {} 个测试用例文件，共 {} 个测试用例",
                testCaseFiles.size(),
                testCaseFiles.stream().mapToInt(f -> f.getCases().size()).sum());
    }

    @Test
    @DisplayName("adminAgent - 本体查询")
    void testAdminOntologyQuery() throws IOException {
        // 本地模拟测试，不依赖真实 API
        // TODO: 集成 SpringBootTest + Testcontainers 跑真实 Agent
        Path yamlPath = TESTCASES_DIR.resolve("admin-agent.yaml");
        if (Files.exists(yamlPath)) {
            TestCaseFile tf = yamlMapper.readValue(yamlPath.toFile(), TestCaseFile.class);
            for (TestCase tc : tf.getCases()) {
                log.info("测试用例: {}", tc.getName());
                log.info("  输入: {}", tc.getInput());
                log.info("  断言数量: {}", tc.getExpected().size());
                // 占位验证：实际断言在集成测试环境执行
            }
        }
    }

    @Data
    public static class TestCaseFile {
        private String agent;
        private List<TestCase> cases;
    }

    @Data
    public static class TestCase {
        private String name;
        private String input;
        private List<Assertion> expected;
    }

    @Data
    public static class Assertion {
        private String type;   // contains | not_contains | matches | is_json
        private String value; // type=contains/not_contains/matches 时使用
        private String pattern; // type=matches 时使用（兼容 value）
    }

    /**
     * 验证断言
     */
    public static class AssertionResult {
        public final String assertionType;
        public final String expected;
        public final boolean passed;
        public final String actual;

        public AssertionResult(String type, String expected, boolean passed, String actual) {
            this.assertionType = type;
            this.expected = expected;
            this.passed = passed;
            this.actual = actual;
        }

        public static AssertionResult pass(String type, String expected, String actual) {
            return new AssertionResult(type, expected, true, actual);
        }

        public static AssertionResult fail(String type, String expected, String actual) {
            return new AssertionResult(type, expected, false, actual);
        }
    }

    public static AssertionResult evaluateAssertion(Assertion assertion, String response) {
        String type = assertion.getType();
        String value = assertion.getValue() != null ? assertion.getValue() : assertion.getPattern();

        return switch (type) {
            case "contains" -> response.contains(value)
                    ? AssertionResult.pass(type, value, "响应包含")
                    : AssertionResult.fail(type, value, "响应不包含 '" + value + "'");
            case "not_contains" -> !response.contains(value)
                    ? AssertionResult.pass(type, value, "响应不含")
                    : AssertionResult.fail(type, value, "响应不应包含 '" + value + "'");
            case "matches" -> {
                boolean matched = response.matches(value);
                yield matched
                        ? AssertionResult.pass(type, value, "正则匹配")
                        : AssertionResult.fail(type, value, "响应不匹配正则 '" + value + "'");
            }
            case "is_json" -> {
                boolean valid;
                try {
                    yamlMapper.readTree(response);
                    valid = true;
                } catch (IOException e) {
                    valid = false;
                }
                yield valid
                        ? AssertionResult.pass(type, "JSON", "有效 JSON")
                        : AssertionResult.fail(type, "JSON", "无效 JSON");
            }
            default -> AssertionResult.fail(type, value, "未知断言类型: " + type);
        };
    }
}