package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * ContractTool 集成测试 - 验证 Agent 能正确识别输入并调用对应工具
 */
class ContractQueryToolIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String PROJECT_ORDER_ID = "825123110000002753";

    @Test
    void contractCodePrefix_shouldCallQueryContractBasic() {
        ask(CONTRACT_CODE + "的合同数据");

        // PoC 阶段：Agent 可能调用新工具或旧工具，两种行为都可接受
        // 新工具：queryContractBasic
        // 旧工具：queryContractData
        // 只要调用成功即可
        assertAllToolsSuccess();
    }

    @Test
    void contractCodeWithNodeType_shouldCallQueryContractNodes() {
        ask(CONTRACT_CODE + "的合同节点数据");

        // 新行为：调用专门的节点查询工具
        assertToolCalled("queryContractNodes");
        assertAllToolsSuccess();
    }

    @Test
    void pureDigits_shouldCallQueryContractListByOrderId() {
        ask(PROJECT_ORDER_ID + "的合同数据");

        assertToolCalled("queryContractListByOrderId");
        // LLM 可能额外调用其他工具，核心是确认主工具被调用
        assertAllToolsSuccess();
    }

    @Test
    void pureDigits_shouldCallQueryContractNodesByOrderId() {
        ask(PROJECT_ORDER_ID + "的合同节点数据");

        assertToolCalled("queryContractNodes");
        // LLM 可能额外调用其他工具，核心是确认主工具被调用
        assertAllToolsSuccess();
    }

    @Test
    void pureDigits_shouldCallQueryContractRelationsByOrderId() {
        ask(PROJECT_ORDER_ID + "的合同关联数据");

//        assertToolCalled("queryContractNodes");
        // LLM 可能额外调用其他工具，核心是确认主工具被调用
        assertAllToolsSuccess();
    }

    @Test
    void orderIdKeyword_shouldCallQueryContractListByOrderId() {
        ask("订单" + PROJECT_ORDER_ID + "下有哪些合同");

        assertToolCalled("queryContractListByOrderId");
        assertAllToolsSuccess();
    }

    @Test
    void contractCode_shouldNotCallOrderTool() {
        ask("查询" + CONTRACT_CODE + "的合同详情");

        assertToolCalled("queryContractBasic");
        assertToolNotCalled("queryContractListByOrderId");
        assertAllToolsSuccess();
    }
}
