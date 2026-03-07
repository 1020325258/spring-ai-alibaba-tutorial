package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单合同列表查询端到端集成测试
 *
 * 前置条件：application-local.yml 配置数据库连接，数据库网络可达
 *
 * 运行：
 *   JAVA_HOME=... mvn test -pl 05-SREmate -Dtest=ContractListQueryIT
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractListQueryIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void askContractList_byOrderId_returnsAggregatedContractData() {
        String question = "826030619000001899有哪些合同";

        String response = sreAgent.prompt()
                .user(question)
                .call()
                .content();

        System.out.println("=== Agent 回复 ===\n" + response);

        assertThat(response).isNotBlank();
        // 回复中应包含合同编号（C 开头）
        assertThat(response).containsPattern("C\\d+");
        assertThat(response).doesNotContain("查询合同列表失败");
        assertThat(response).doesNotContain("未找到合同记录");
    }
}
