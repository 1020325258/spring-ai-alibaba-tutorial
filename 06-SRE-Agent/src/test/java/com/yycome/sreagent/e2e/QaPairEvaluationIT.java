package com.yycome.sreagent.e2e;

import com.yycome.sreagent.trigger.http.ChatController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 问答对评估测试
 *
 * <p>从 YAML 文件加载问答对，使用 LLM-as-Judge 进行语义评估。
 *
 * <h3>维护方式</h3>
 * 只需修改 {@code src/test/resources/qa-pairs/sre-agent-qa.yaml}，无需改代码。
 *
 * <h3>YAML 格式</h3>
 * <pre>
 * qa-pairs:
 *   - id: "query-contract-basic"
 *     question: "C1767173898135504的合同基本信息"
 *     expected: "返回该合同的基本信息，应包含合同号、合同状态等核心字段"
 * </pre>
 *
 * <h3>运行命令</h3>
 * <pre>
 * JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 * mvn test -f ../pom.xml -pl 06-SRE-Agent -Dtest=QaPairEvaluationIT
 * </pre>
 */
@DisplayName("问答对评估测试")
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class QaPairEvaluationIT {

    private static final Path REPORT_PATH = Path.of("docs/test-execution-report.md");

    private static List<QaPair> qaPairs;
    private static List<QaEvaluationReporter.QaEvaluationResult> evaluationResults;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ChatController chatController;

    @BeforeAll
    static void setUp() {
        qaPairs = QaPairLoader.load("qa-pairs/sre-agent-qa.yaml");
        evaluationResults = new CopyOnWriteArrayList<>();
        System.out.println("已加载 " + qaPairs.size() + " 个问答对");
    }

    @AfterAll
    static void generateReport() {
        QaEvaluationReporter reporter = new QaEvaluationReporter(REPORT_PATH);
        reporter.generate(new ArrayList<>(evaluationResults));
        System.out.println("评估报告已生成: " + REPORT_PATH.toAbsolutePath());
    }

    /**
     * 动态生成测试用例，每个问答对对应一个测试
     */
    @TestFactory
    @DisplayName("问答对评估")
    Stream<DynamicTest> qaPairTests() {
        return qaPairs.stream()
                .map(qa -> dynamicTest(
                        qa.id(),
                        () -> evaluateQaPair(qa)
                ));
    }

    /**
     * 评估单个 QA pair - 直接调用 ChatController.streamAndCollect()
     * 重构源代码，使其可测：直接调用方法而非 HTTP 调用
     */
    private void evaluateQaPair(QaPair qa) {
        // 1. 直接调用 ChatController 方法获取输出
        String actualOutput = chatController.streamAndCollect(qa.question());

        // 2. 使用 Judge 进行语义评估
        EvaluationJudge judge = new EvaluationJudge(chatModel);
        EvaluationJudge.JudgeResult judgeResult = judge.evaluate(
                qa.question(),
                actualOutput,
                qa.expected()
        );

        // 3. 收集结果用于报告生成
        evaluationResults.add(new QaEvaluationReporter.QaEvaluationResult(
                qa,
                actualOutput,
                judgeResult
        ));

        // 4. 断言
        assertThat(judgeResult.pass())
                .as("[%s] 评估不通过: %s", qa.id(), judgeResult.reason())
                .isTrue();
    }
}
