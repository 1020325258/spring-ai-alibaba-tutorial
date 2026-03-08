package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同数据查询端到端集成测试（4种 dataType 场景）
 *
 * 前置条件：application-local.yml 配置数据库连接，数据库网络可达
 *
 * 运行全部：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT
 *
 * 运行单个：
 *   JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
 *     mvn test -pl 05-SREmate -Dtest=ContractDataQueryIT#queryAllData_returnsFullData
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class ContractDataQueryIT {

    // 替换为数据库中真实存在的合同编号
    private static final String CONTRACT_CODE = "C1772854666284956";

    @Autowired
    private ChatClient sreAgent;

    @Test
    void queryAllData_returnsFullData() {
        String response = ask(CONTRACT_CODE + "合同数据");

        System.out.println("=== [ALL] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contractCode");
        assertThat(response).contains("contract_node");
        assertThat(response).contains("contract_user");
        assertThat(response).contains("contract_field_sharding");
        assertThat(response).contains("contract_quotation_relation");
        assertThat(response).doesNotContain("contract_log");
    }

    @Test
    void queryNodeData_returnsNodeAndLog() {
        String response = ask(CONTRACT_CODE + "合同节点数据");

        System.out.println("=== [CONTRACT_NODE] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_node");
        assertThat(response).contains("contract_log");
        assertThat(response).doesNotContain("contract_user");
        assertThat(response).doesNotContain("contract_field_sharding");
    }

    @Test
    void queryFieldData_returnsFieldSharding() {
        String response = ask(CONTRACT_CODE + "合同字段数据");

        System.out.println("=== [CONTRACT_FIELD] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_field_sharding");
        assertThat(response).doesNotContain("contract_node");
        assertThat(response).doesNotContain("contract_user");
    }

    @Test
    void queryUserData_returnsContractUser() {
        String response = ask(CONTRACT_CODE + "合同签约人数据");

        System.out.println("=== [CONTRACT_USER] Agent 回复 ===\n" + response);

        assertThat(response).doesNotContain("\"error\"");
        assertThat(response).contains("contract_user");
        assertThat(response).doesNotContain("contract_node");
        assertThat(response).doesNotContain("contract_field_sharding");
    }

    private String ask(String question) {
        return sreAgent.prompt()
                .user(question)
                .call()
                .content();
    }
}
