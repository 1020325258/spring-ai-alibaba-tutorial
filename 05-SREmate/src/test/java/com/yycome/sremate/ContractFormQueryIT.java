package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同版式查询端到端集成测试
 *
 * 前置条件：
 *   1. application-local.yml 配置数据库连接
 *   2. 数据库和 i.nrs-sales-project.home.ke.com 网络可达
 *
 * 运行：
 *   JAVA_HOME=... mvn test -pl 05-SREmate -Dtest=ContractFormQueryIT
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractFormQueryIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void askContractFormId_byContractCode_returnsFormId() {
        String question = "查询合同编号 C1772854666284956 的 form_id";

        String response = sreAgent.prompt()
                .user(question)
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);

        assertThat(response)
                .as("回复不应为空")
                .isNotBlank();
        assertThat(response)
                .as("回复中应包含 form_id 相关内容")
                .containsIgnoringCase("form");
        assertThat(response)
                .as("回复不应提示查询失败")
                .doesNotContain("查询失败")
                .doesNotContain("未找到合同编号");
    }
}
