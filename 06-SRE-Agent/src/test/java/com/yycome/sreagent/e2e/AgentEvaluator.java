package com.yycome.sreagent.e2e;

import com.yycome.sreagent.config.node.AdminNode;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.yycome.sreagent.e2e.AgentEvaluationIT.AssertionResult;
import static com.yycome.sreagent.e2e.AgentEvaluationIT.evaluateAssertion;

/**
 * Agent 评估器
 *
 * 加载 YAML 格式的问答对，对指定 Agent 执行评估，输出详细报告。
 *
 * 支持的目标 agent 类型：
 * - adminNode: 直接调用 AdminNode（环境切换/本体查询）
 *
 * 用法示例：
 * <pre>
 * AgentEvaluator evaluator = new AgentEvaluator.Builder()
 *     .targetAgent(new AdminNode(envConfig, adminAgent, tracingService))
 *     .build();
 *
 * TestCase tc = new TestCase();
 * tc.setInput("本体模型有哪些");
 * tc.setExpected(List.of(new Assertion("contains", "Order", null)));
 *
 * EvaluationResult result = evaluator.evaluate(tc);
 * log.info("Passed: {}, Details: {}", result.isPassed(), result.getDetails());
 * </pre>
 */
@Slf4j
public class AgentEvaluator {

    private final TargetAgent targetAgent;

    public AgentEvaluator(TargetAgent targetAgent) {
        this.targetAgent = targetAgent;
    }

    /**
     * 对单个测试用例执行评估
     */
    public EvaluationResult evaluate(TestCase testCase) {
        log.info("=== 执行评估: {} ===", testCase.getName());
        log.info("  输入: {}", testCase.getInput());

        String response;
        try {
            response = targetAgent.call(testCase.getInput());
        } catch (Exception e) {
            log.error("Agent 调用失败: {}", e.getMessage());
            return EvaluationResult.failed(testCase, "Agent 调用异常: " + e.getMessage());
        }

        log.info("  响应: {}", response.length() > 200 ? response.substring(0, 200) + "..." : response);

        List<AssertionResult> assertionResults = new ArrayList<>();
        boolean allPassed = true;

        for (var assertion : testCase.getExpected()) {
            AssertionResult result = evaluateAssertion(assertion, response);
            assertionResults.add(result);
            if (!result.passed) {
                allPassed = false;
                log.warn("  ✗ 断言 [{}] 失败: expected '{}', actual: {}",
                        assertion.getType(),
                        result.expected,
                        result.actual);
            } else {
                log.info("  ✓ 断言 [{}] 通过: '{}'", assertion.getType(), result.expected);
            }
        }

        EvaluationResult er = new EvaluationResult(testCase, allPassed, response, assertionResults);
        log.info("  结果: {}", allPassed ? "✓ PASS" : "✗ FAIL");
        return er;
    }

    /**
     * 对多个测试用例执行评估并生成报告
     */
    public EvaluationReport evaluateAll(List<TestCase> testCases) {
        List<EvaluationResult> results = new ArrayList<>();
        for (TestCase tc : testCases) {
            results.add(evaluate(tc));
        }
        return new EvaluationReport(results);
    }

    // --- 内部数据结构 ---

    @Data
    public static class EvaluationResult {
        private final TestCase testCase;
        private final boolean passed;
        private final String response;
        private final List<AssertionResult> assertionResults;

        public EvaluationResult(TestCase testCase, boolean passed,
                                String response, List<AssertionResult> assertionResults) {
            this.testCase = testCase;
            this.passed = passed;
            this.response = response;
            this.assertionResults = assertionResults;
        }

        public static EvaluationResult failed(TestCase testCase, String error) {
            AssertionResult errResult = AgentEvaluationIT.AssertionResult.fail("error", error, error);
            return new EvaluationResult(testCase, false, error, List.of(errResult));
        }
    }

    @Data
    public static class EvaluationReport {
        private final List<EvaluationResult> results;

        public int getTotal() { return results.size(); }
        public int getPassed() { return (int) results.stream().filter(EvaluationResult::isPassed).count(); }
        public int getFailed() { return getTotal() - getPassed(); }

        public List<EvaluationResult> getFailedResults() {
            return results.stream().filter(r -> !r.isPassed()).toList();
        }

        public void printReport() {
            log.info("\n========== 评估报告 ==========");
            log.info("总计: {} | 通过: {} | 失败: {}",
                    getTotal(), getPassed(), getFailed());
            log.info("---------------------------------");
            for (EvaluationResult r : results) {
                String icon = r.isPassed() ? "✓" : "✗";
                log.info("{} {} - {}", icon, r.getTestCase().getName(),
                        r.isPassed() ? "PASS" : "FAIL");
                if (!r.isPassed()) {
                    for (AssertionResult ar : r.getAssertionResults()) {
                        if (!ar.passed) {
                            log.info("    断言 [{}] 期望 '{}' 但实际: {}",
                                    ar.assertionType, ar.expected, ar.actual);
                        }
                    }
                }
            }
            log.info("================================\n");
        }
    }

    /**
     * 目标 Agent 接口
     */
    public interface TargetAgent {
        String call(String input) throws Exception;
    }

    /**
     * AdminNode 适配器
     */
    public static class AdminNodeAdapter implements TargetAgent {
        private final AdminNode adminNode;

        public AdminNodeAdapter(AdminNode adminNode) {
            this.adminNode = adminNode;
        }

        @Override
        @SuppressWarnings("unchecked")
        public String call(String input) throws Exception {
            // AdminNode.apply() 返回 Map<String, Object>
            // 实际测试中使用 Spring 注入的 bean
            throw new UnsupportedOperationException(
                    "AdminNodeAdapter 需要通过 Spring 上下文注入 AdminNode bean，请使用 SpringBootTest");
        }
    }

    @Data
    public static class TestCase {
        private String name;
        private String input;
        private List<Assertion> expected;
    }

    @Data
    public static class Assertion {
        private String type;  // contains | not_contains | matches | is_json
        private String value;
        private String pattern;
    }
}