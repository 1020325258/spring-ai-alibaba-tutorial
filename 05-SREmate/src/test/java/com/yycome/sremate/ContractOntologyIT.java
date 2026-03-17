package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 本体论驱动的合同查询集成测试
 * 验证 ontologyQuery 统一入口工具的查询路径正确性
 */
class ContractOntologyIT extends BaseSREIT {

    // ── 合同号查询：entity=Contract ────────────────────────

    @Test
    void contractBasic_shouldUseContractEntity() {
        ask("C1767173898135504的合同基本信息");
        assertOntologyQueryParams("Contract", null);
        assertAllToolsSuccess();
    }

    @Test
    void contractNodes_shouldUseContractEntityAndContractNodeScope() {
        ask("C1767173898135504的合同节点");
        assertOntologyQueryParams("Contract", "ContractNode");
        assertAllToolsSuccess();
    }

    @Test
    void contractSignedObjects_shouldUseContractEntityAndQuotationRelationScope() {
        ask("C1767173898135504的签约单据");
        assertOntologyQueryParams("Contract", "ContractQuotationRelation");
        assertAllToolsSuccess();
    }

    @Test
    void contractFields_shouldUseContractEntityAndFieldScope() {
        ask("C1767173898135504的合同字段");
        assertOntologyQueryParams("Contract", "ContractField");
        assertAllToolsSuccess();
    }

    // ── 订单号查询：entity=Order ──────────────────

    @Test
    void orderContract_shouldUseOrderEntityAndContractScope() {
        ask("825123110000002753下的合同");
        assertOntologyQueryParams("Order", "Contract");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_signedObjectsAndNodes_shouldUseOrderEntityAndMultipleScopes() {
        ask("825123110000002753合同签约单据和节点");
        assertOntologyQueryParams("Order", "ContractNode,ContractQuotationRelation");
        assertAllToolsSuccess();
    }

    // ── 报价单查询：entity=Order, queryScope=BudgetBill ────────────────────────

    @Test
    void budgetBill_shouldUseOrderEntityAndBudgetBillScope() {
        ask("826031111000001859的报价单");
        assertOntologyQueryParams("Order", "BudgetBill");
        assertAllToolsSuccess();
    }

    // ── S单查询：entity=Order, queryScope=SubOrder ────────────────────────

    @Test
    void subOrder_shouldUseOrderEntityAndSubOrderScope() {
        ask("826031111000001859的S单");
        assertOntologyQueryParams("Order", "SubOrder");
        assertAllToolsSuccess();
    }

    // ── 版式/配置表查询 ──────

    @Test
    void contractForm_shouldUseContractEntityAndContractFormScope() {
        ask("C1773303150687211的版式");
        assertOntologyQueryParams("Contract", "ContractForm");
        assertAllToolsSuccess();
    }

    @Test
    void contractConfig_shouldUseContractEntityAndConfigScope() {
        ask("C1767173898135504的配置表");
        assertOntologyQueryParams("Contract", "ContractConfig");
        assertAllToolsSuccess();
    }
}
