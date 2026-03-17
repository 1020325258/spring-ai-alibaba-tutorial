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
        assertAllToolsSuccess();
    }

    @Test
    void contractNodes_shouldCallOntologyQuery() {
        ask("C1767173898135504的合同节点");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    @Test
    void contractSignedObjects_shouldCallOntologyQuery() {
        ask("C1767173898135504的签约单据");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    @Test
    void contractFields_shouldCallOntologyQuery() {
        ask("C1767173898135504的合同字段");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    // ── 订单号查询：使用 ontologyQuery(entity=Order) ──────────────────

    @Test
    void orderContract_shouldCallOntologyQuery() {
        ask("825123110000002753下的合同");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_signedObjectsAndNodes_shouldCallOntologyQuery() {
        ask("825123110000002753合同签约单据和节点");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    // ── 报价单查询：使用 ontologyQuery(entity=Order, queryScope=BudgetBill) ────────────────────────

    @Test
    void budgetBill_shouldCallOntologyQuery() {
        ask("826031111000001859的报价单");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    // ── S单查询：使用 ontologyQuery(entity=Order, queryScope=SubOrder) ────────────────────────

    @Test
    void subOrder_shouldCallOntologyQuery() {
        ask("826031111000001859的S单");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    // ── 版式/配置表查询：使用 ontologyQuery(entity=Contract, queryScope=form/config) ──────

    @Test
    void contractForm_shouldCallOntologyQuery() {
        ask("C1773303150687211的版式");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    @Test
    void contractConfig_shouldCallOntologyQuery() {
        ask("C1767173898135504的配置表");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }
}
