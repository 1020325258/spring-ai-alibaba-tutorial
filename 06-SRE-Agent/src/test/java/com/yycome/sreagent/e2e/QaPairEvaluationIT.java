package com.yycome.sreagent.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * 问答对评估测试
 *
 * <p>从 YAML 文件加载问答对，动态生成测试用例。
 *
 * <h3>维护方式</h3>
 * 只需修改 {@code src/test/resources/qa-pairs/sre-agent-qa.yaml}，无需改代码。
 *
 * <h3>YAML 格式</h3>
 * <pre>
 * qa-pairs:
 *   - id: "query-contract-basic"
 *     question: "C1767173898135504的合同基本信息"
 *     expected:
 *       type: "tool_call"
 *       tool: "ontologyQuery"
 *       params:
 *         entity: "Contract"
 *     also:
 *       type: "json_output"
 *       queryEntity: "Contract"
 * </pre>
 *
 * <h3>运行命令</h3>
 * <pre>
 * JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 * mvn test -f ../pom.xml -pl 06-SRE-Agent -Dtest=QaPairEvaluationIT
 * </pre>
 */
@DisplayName("问答对评估测试")
public class QaPairEvaluationIT extends BaseSREAgentIT {

    private static List<QaPair> qaPairs;

    @BeforeAll
    static void loadQaPairs() {
        qaPairs = QaPairLoader.load("qa-pairs/sre-agent-qa.yaml");
        System.out.println("已加载 " + qaPairs.size() + " 个问答对");
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
                        () -> assertQaPairCompliance(qa)
                ));
    }
}
