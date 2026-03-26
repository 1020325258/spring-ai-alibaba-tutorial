package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 本体论驱动的合同查询集成测试
 *
 * 验证两层语义：
 *   1. 意图识别层：LLM 是否传了正确的 entity/queryScope 参数
 *   2. 数据输出层：输出 JSON 结构是否正确（queryEntity、records 存在、关键字段存在）
 *
 * 验证策略（避免维护成本剧增）：
 *   - 只验证 queryEntity/queryValue 元数据（稳定）
 *   - 只验证关键字段"存在"，不验证具体值（业务数据会变）
 *   - ID 回显字段（如 instanceId）可精确验证（值等于查询输入，非常稳定）
 */
class ContractOntologyIT extends BaseSREIT {

    // ── 合同号查询：entity=Contract ────────────────────────

    @Test
    void contractBasic_shouldUseContractEntity() {
        ask("C1767173898135504的合同基本信息");

        // 意图识别
        assertOntologyQueryParams("Contract", null);
        assertAllToolsSuccess();

        // 输出结构：应返回合同实体，records 包含 contractCode
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
        assertFirstRecordHasField("contractCode");
    }

    @Test
    void contractNodes_shouldUseContractEntityAndContractNodeScope() {
        ask("C1767173898135504的合同节点");

        // 意图识别
        assertOntologyQueryParams("Contract", "ContractNode");
        assertAllToolsSuccess();

        // 输出结构：应包含节点列表
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
    }

    @Test
    void contractSignedObjects_shouldUseContractEntityAndQuotationRelationScope() {
        ask("C1767173898135504的签约单据");

        // 意图识别
        assertOntologyQueryParams("Contract", "ContractQuotationRelation");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
    }

    @Test
    void contractFields_shouldUseContractEntityAndFieldScope() {
        ask("C1767173898135504的合同字段");

        // 意图识别
        assertOntologyQueryParams("Contract", "ContractField");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
    }

    // ── 订单号查询：entity=Order ──────────────────

    @Test
    void orderContract_shouldUseOrderEntityAndContractScope() {
        ask("825123110000002753下的合同");

        // 意图识别
        assertOntologyQueryParams("Order", "Contract");
        assertAllToolsSuccess();

        // 输出结构：queryEntity 是起始实体 Order，records[0] 包含 contracts 子数组
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
        // Order 实体展开后，contracts 嵌套在 records[0] 中
        assertFirstRecordHasField("contracts");
    }

    @Test
    void orderContract_signedObjectsAndNodes_shouldUseOrderEntityAndMultipleScopes() {
        ask("825123110000002753合同签约单据和节点");

        // 意图识别
        assertOntologyQueryParams("Order", "ContractNode,ContractQuotationRelation");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
    }

    // ── 报价单查询：entity=Order, queryScope=BudgetBill ────────────────────────

    @Test
    void budgetBill_shouldUseOrderEntityAndBudgetBillScope() {
        ask("826031111000001859的报价单");

        // 意图识别
        assertOntologyQueryParams("Order", "BudgetBill");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
    }

    // ── S单查询：entity=Order, queryScope=SubOrder ────────────────────────

    @Test
    void subOrder_shouldUseOrderEntityAndSubOrderScope() {
        ask("826031111000001859的S单");

        // 意图识别
        assertOntologyQueryParams("Order", "SubOrder");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
    }

    // ── S单直查：entity=Order, queryScope=SubOrder（订单号直查） ────────────────────────

    @Test
    void subOrder_directFromOrder_shouldReturnSubOrders() {
        // 直接使用订单号查询 S 单，验证 Order → SubOrder 直查路径
        ask("825123110000002753的S单列表");

        // 意图识别：Order → SubOrder 直查
        assertOntologyQueryParams("Order", "SubOrder");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
    }

    // ── SignableOrderInfo 多跳查询：Order → Contract → SignableOrderInfo ────────────────────────

    @Test
    void signableOrderInfo_shouldTraverseFromOrder() {
        // 从订单号出发，查询 SignableOrderInfo（需要先查 Contract 获取 type）
        // 使用已知有 type=8 销售合同的订单
        ask("825123117000001474的弹窗S单");

        // 意图识别：Order → Contract → SignableOrderInfo 多跳遍历
        // 需要先确定实体类型，再确定 queryScope
        assertAllToolsSuccess();

        // 输出结构验证：应能返回 SignableOrderInfo 数据
        assertOutputHasRecords();
    }

    // ── 版式/配置表查询 ──────

    @Test
    void contractInstance_shouldUseContractEntityAndContractInstanceScope() {
        ask("C1773303150687211的版式");

        // 意图识别
        assertOntologyQueryParams("Contract", "ContractInstance");
        assertAllToolsSuccess();

        // 输出结构：Contract 实体展开后，contractInstances 嵌套在 records[0] 中
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
        // Contract 实体展开后，contractInstances 嵌套在 records[0] 中
        assertFirstRecordHasField("contractInstances");
    }

    @Test
    void contractConfig_shouldUseContractEntityAndConfigScope() {
        ask("C1767173898135504的配置表");

        // 意图识别
        assertOntologyQueryParams("Contract", "ContractConfig");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Contract");
        assertOutputHasRecords();
    }

    // ── 个性化报价查询：通过 ontologyQuery 的 PersonalQuote scope ──────

    @Test
    void personalQuote_withSubOrder_shouldUsePersonalQuoteScope() {
        ask("826031210000003581下S15260312120004471的个性化报价");

        // 意图识别：应使用 ontologyQuery，queryScope=PersonalQuote
        assertOntologyQueryParams("Order", "PersonalQuote");
        assertAllToolsSuccess();

        // 输出结构：Order → contracts → contractQuotationRelations → personalQuotes 三跳层级
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
        // records[0] 是 Order，应展开 contracts 子记录
        assertFirstRecordHasField("contracts");
    }

    @Test
    void personalQuote_withBillCode_shouldUsePersonalQuoteScope() {
        ask("826031210000003581下GBILL260312104241050001的个性化报价");

        // 意图识别
        assertOntologyQueryParams("Order", "PersonalQuote");
        assertAllToolsSuccess();

        // 输出结构
        assertOutputField("queryEntity", "Order");
        assertOutputHasRecords();
        assertFirstRecordHasField("contracts");
    }

    // ── ContractInstance 直接查询 ──────
    // 这是曾经出错的用例（输出数据不符合预期），故做更细致的验证

    @Test
    void contractInstance_directQuery_shouldUseContractInstanceEntity() {
        ask("145801的实例信息");

        // 意图识别：直接以 ContractInstance 为起始实体，不展开关联
        assertOntologyQueryParams("ContractInstance", null);
        assertAllToolsSuccess();

        // 输出结构验证（曾经出错：queryEntity 返回了错误的实体类型）
        assertOutputField("queryEntity", "ContractInstance");
        assertOutputField("queryValue", "145801");
        assertOutputHasRecords();

        // 关键字段验证：instanceId 应回显查询输入值（稳定）
        assertFirstRecordFieldEquals("instanceId", "145801");

        // formData 结构应存在
        assertFirstRecordHasField("formData");
        assertFirstRecordHasField("formData/id");
        assertFirstRecordHasField("formData/formId");
    }
}
