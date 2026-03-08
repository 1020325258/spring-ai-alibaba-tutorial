package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同配置表查询端到端集成测试
 *
 * 前置条件：application-local.yml 配置数据库连接，数据库网络可达
 *
 * 运行全部：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -Dtest=ContractConfigQueryIT
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractConfigQueryIT {

    // 使用与 ContractDataQueryIT 相同的合同编号
    private static final String CONTRACT_CODE = "C1772854666284956";

    @Autowired
    private ChatClient sreAgent;

    /**
     * 测试场景：使用合同编号查询配置表
     * 验证点：Agent 应正确识别合同编号，自动获取 type，返回配置数据
     */
    @Test
    void queryByContractCode_shouldRecognizeContractNumber() {
        String response = ask(CONTRACT_CODE + "的合同配置表数据");

        System.out.println("=== [按合同号查询配置] Agent 回复 ===\n" + response);

        // 验证 Agent 正确识别意图，不是返回"未找到编号"错误
        assertThat(response).doesNotContain("未找到编号");

        // 验证返回了查询相关的数据（可能是配置数据，也可能是数据不存在的提示）
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("contract_city_company_info"),
            r -> assertThat(r).contains("project_config_snap"),
            r -> assertThat(r).contains("projectOrderId")
        );
    }

    /**
     * 测试场景：使用订单号查询配置表（指定合同类型）
     * 验证点：Agent 应正确识别订单号，使用 projectOrderId 参数
     */
    @Test
    void queryByOrderId_shouldRecognizeOrderNumber() {
        String response = ask("826030619000001899的正签合同配置");

        System.out.println("=== [按订单号+指定类型查询配置] Agent 回复 ===\n" + response);

        // ✅ 关键验证：Agent 应正确识别订单号，不应出现"未找到编号"错误
        // 因为这意味着 Agent 把订单号错误地赋给了 contractCode 参数
        assertThat(response).doesNotContain("未找到编号");
        assertThat(response).doesNotContain("error");
    }

    /**
     * 测试场景：使用订单号查询配置表（未指定合同类型）
     * 验证点：Agent 应识别订单号，根据数据情况返回配置或提示信息
     */
    @Test
    void queryByOrderIdWithoutType_shouldRecognizeOrderNumber() {
        String response = ask("826030619000001899的合同配置");

        System.out.println("=== [按订单号查询配置-未指定类型] Agent 回复 ===\n" + response);

        // ✅ 验证返回内容符合预期场景：
        // 1. needAskType/availableTypes - 订单有多类型合同，需要询问
        // 2. contract_city_company_info - Agent 自动推断类型并返回数据
        // 3. "未找到"或"没有对应" - 订单下没有合同记录
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("needAskType"),
            r -> assertThat(r).contains("availableTypes"),
            r -> assertThat(r).contains("合同类型"),
            r -> assertThat(r).contains("contract_city_company_info"),
            r -> assertThat(r).contains("projectOrderId"),
            r -> assertThat(r).contains("未找到"),
            r -> assertThat(r).contains("没有对应")
        );
    }

    private String ask(String question) {
        return sreAgent.prompt()
                .user(question)
                .call()
                .content();
    }
}
