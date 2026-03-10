package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同配置表查询集成测试
 * 验证按合同号和订单号查询 contract_city_company_info 的链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractConfigToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String PROJECT_ORDER_ID = "825123110000002753";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void queryContractConfig_byContractCode_shouldReturnConfig() {
        String response = ask(CONTRACT_CODE + "的合同配置表数据");

        assertThat(response).doesNotContain("未找到编号");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info"),
                r -> assertThat(r).containsIgnoringCase("projectOrderId"),
                r -> assertThat(r).containsIgnoringCase("company")
        );
    }

    @Test
    void queryContractConfig_byOrderId_withContractType_shouldReturnConfig() {
        String response = ask(PROJECT_ORDER_ID + "的销售合同配置");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到编号");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info"),
                r -> assertThat(r).containsIgnoringCase("projectOrderId"),
                r -> assertThat(r).contains(PROJECT_ORDER_ID)
        );
    }

    @Test
    void queryContractConfig_byOrderId_withoutType_shouldAskForType() {
        String response = ask(PROJECT_ORDER_ID + "的合同配置");

        assertThat(response).doesNotContain("未找到编号");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsAnyOf("类型", "合同类型", "正签", "认购", "needAskType"),
                r -> assertThat(r).containsIgnoringCase("contract_city_company_info")
        );
    }
}
