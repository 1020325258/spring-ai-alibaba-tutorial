package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 本体论驱动的合同查询集成测试
 * 验证 ontologyQuery 统一入口工具能被正确触发
 */
class ContractOntologyIT extends BaseSREIT {

    // ── 合同号查询：使用 ontologyQuery(entity=Contract) ────────────────────────

    @Test
    void contractBasic_shouldCallOntologyQuery() {
        ask("C1767173898135504的合同基本信息");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的拆分工具
        assertToolNotCalled("queryContractBasic");
        assertAllToolsSuccess();
    }

    @Test
    void contractNodes_shouldCallOntologyQuery() {
        ask("C1767173898135504的合同节点");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的拆分工具
        assertToolNotCalled("queryContractNodes");
        assertAllToolsSuccess();
    }

    @Test
    void contractSignedObjects_shouldCallOntologyQuery() {
        ask("C1767173898135504的签约单据");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的拆分工具
        assertToolNotCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void contractFields_shouldCallOntologyQuery() {
        ask("C1767173898135504的合同字段");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的拆分工具
        assertToolNotCalled("queryContractFields");
        assertAllToolsSuccess();
    }

    // ── 订单号查询：使用 ontologyQuery(entity=Order) ──────────────────

    @Test
    void orderContract_allData_shouldCallOntologyQuery() {
        ask("825123110000002753下的合同数据");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的工具
        assertToolNotCalled("queryContractsByOrderId");
        assertToolNotCalled("queryContractBasic");
        assertToolNotCalled("queryContractNodes");
        assertToolNotCalled("queryContractFields");
        assertToolNotCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_signedObjects_shouldCallOntologyQuery() {
        ask("825123110000002753合同的签约单据");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的工具
        assertToolNotCalled("queryContractsByOrderId");
        assertToolNotCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_contractNodes_shouldCallOntologyQuery() {
        ask("825123110000002753合同节点");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的工具
        assertToolNotCalled("queryContractsByOrderId");
        assertToolNotCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_contractSignedObjAndNodes_shouldCallOntologyQuery() {
        ask("825123110000002753合同签约单据和节点");
        assertToolCalled("ontologyQuery");
        // 禁止调用旧的工具
        assertToolNotCalled("queryContractsByOrderId");
        assertToolNotCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }
}
