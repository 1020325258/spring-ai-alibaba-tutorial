package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同实例 ID 和版式 form_id 查询集成测试
 * queryContractFormId 是复合工具（DB + HTTP），同时验证两段链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractInstanceToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String CONTRACT_CODE = "C1767173898135504";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void queryContractInstanceId_shouldReturnInstanceId() {
        String response = ask(CONTRACT_CODE + "的 platform_instance_id 是多少");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("platformInstanceId"),
                r -> assertThat(r).containsIgnoringCase("platform_instance_id"),
                r -> assertThat(r).containsIgnoringCase("instanceId")
        );
    }

    @Test
    void queryContractFormId_shouldReturnFormId() {
        String response = ask(CONTRACT_CODE + "的版式 form_id 是多少");

        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("form_id"),
                r -> assertThat(r).containsIgnoringCase("formId"),
                r -> assertThat(r).containsIgnoringCase("版式")
        );
    }
}
