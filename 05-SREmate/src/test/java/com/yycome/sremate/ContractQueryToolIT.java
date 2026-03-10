package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractTool 集成测试 - 验证按合同号和订单号查询合同数据的完整链路
 */
class ContractQueryToolIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String PROJECT_ORDER_ID = "825123110000002753";

    @Test
    void queryContractData_withContractCode_shouldReturnContractData() {
        String response = ask(CONTRACT_CODE + "的合同数据");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_code"),
                r -> assertThat(r).containsIgnoringCase("contract_status"),
                r -> assertThat(r).containsIgnoringCase(CONTRACT_CODE)
        );
    }

    @Test
    void queryContractData_contractNodeType_shouldReturnNodeData() {
        String response = ask(CONTRACT_CODE + "的合同节点数据");

        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_node"),
                r -> assertThat(r).containsIgnoringCase("node_type"),
                r -> assertThat(r).containsIgnoringCase("node_status")
        );
    }

    @Test
    void queryContractData_contractUserType_shouldReturnUserData() {
        String response = ask(CONTRACT_CODE + "的签约人信息");

        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_user"),
                r -> assertThat(r).containsIgnoringCase("user_name"),
                r -> assertThat(r).containsIgnoringCase("user_type")
        );
    }

    @Test
    void queryContractData_withCPrefix_shouldNotUseOrderTool() {
        String response = ask("查询" + CONTRACT_CODE + "的合同详情");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到编号");
    }

    @Test
    void queryContractsByOrderId_withOrderId_shouldReturnContractList() {
        String response = ask(PROJECT_ORDER_ID + "的合同详情");

        assertThat(response).doesNotContain("error");
        assertThat(response).doesNotContain("未找到");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract_code"),
                r -> assertThat(r).containsIgnoringCase("project_order_id"),
                r -> assertThat(r).contains(PROJECT_ORDER_ID)
        );
    }

    @Test
    void queryContractsByOrderId_pureDigits_shouldNotUseContractTool() {
        String response = ask("订单" + PROJECT_ORDER_ID + "下有哪些合同");

        assertThat(response).doesNotContain("无效的 dataType");
        assertThat(response).doesNotContain("error");
    }
}
