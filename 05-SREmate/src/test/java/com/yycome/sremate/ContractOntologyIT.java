package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 本体论驱动的合同查询集成测试
 * 验证新的拆分工具方法能被正确触发
 */
class ContractOntologyIT extends BaseSREIT {

    // ── 新方法：按实体拆分 ────────────────────────────────

    @Test
    void contractBasic_shouldCallQueryContractBasic() {
        ask("C1767173898135504的合同基本信息");
        assertToolCalled("queryContractBasic");
        assertAllToolsSuccess();
    }

    @Test
    void contractNodes_shouldCallQueryContractNodes() {
        ask("C1767173898135504的合同节点");
        assertToolCalled("queryContractNodes");
        assertAllToolsSuccess();
    }

    @Test
    void contractSignedObjects_shouldCallQueryContractSignedObjects() {
        ask("C1767173898135504的签约单据");
        assertToolCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void contractFields_shouldCallQueryContractFields() {
        ask("C1767173898135504的合同字段");
        assertToolCalled("queryContractFields");
        assertAllToolsSuccess();
    }

    // ── 多跳查询：订单 → 合同 → 子实体 ──────────────────

    @Test
    void orderContract_allData_shouldCallContractsByOrderIdThenSubTools() {
        ask("825123110000002753下的合同数据");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractBasic");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_signedObjects_shouldCallSignedObjectsTool() {
        ask("825123110000002753合同的签约单据");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }
}
