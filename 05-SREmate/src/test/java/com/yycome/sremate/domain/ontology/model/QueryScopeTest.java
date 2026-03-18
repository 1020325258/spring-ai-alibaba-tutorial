package com.yycome.sremate.domain.ontology.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * QueryScope 枚举测试
 */
class QueryScopeTest {

    // ── fromString 测试 ───────────────────────────────────

    @Test
    void fromString_null_shouldReturnDefault() {
        QueryScope result = QueryScope.fromString(null);
        assertThat(result).isEqualTo(QueryScope.DEFAULT);
    }

    @Test
    void fromString_enumName_shouldMatch() {
        assertThat(QueryScope.fromString("CONTRACT")).isEqualTo(QueryScope.CONTRACT);
        assertThat(QueryScope.fromString("contract")).isEqualTo(QueryScope.CONTRACT);
        assertThat(QueryScope.fromString("Contract")).isEqualTo(QueryScope.CONTRACT);
    }

    @Test
    void fromString_value_shouldMatch() {
        assertThat(QueryScope.fromString("Contract")).isEqualTo(QueryScope.CONTRACT);
        assertThat(QueryScope.fromString("ContractNode")).isEqualTo(QueryScope.CONTRACT_NODE);
        assertThat(QueryScope.fromString("list")).isEqualTo(QueryScope.LIST);
        assertThat(QueryScope.fromString("default")).isEqualTo(QueryScope.DEFAULT);
    }

    @Test
    void fromString_unknownValue_shouldReturnNull() {
        // 未知值返回 null，表示需要动态处理
        assertThat(QueryScope.fromString("UnknownEntity")).isNull();
        assertThat(QueryScope.fromString("SomeCustomEntity")).isNull();
    }

    // ── getTargetEntity 测试 ───────────────────────────────

    @Test
    void getTargetEntity_defaultAndList_shouldReturnNull() {
        assertThat(QueryScope.DEFAULT.getTargetEntity()).isNull();
        assertThat(QueryScope.LIST.getTargetEntity()).isNull();
    }

    @Test
    void getTargetEntity_domainEntity_shouldReturnValue() {
        assertThat(QueryScope.CONTRACT.getTargetEntity()).isEqualTo("Contract");
        assertThat(QueryScope.CONTRACT_NODE.getTargetEntity()).isEqualTo("ContractNode");
        assertThat(QueryScope.CONTRACT_QUOTATION_RELATION.getTargetEntity()).isEqualTo("ContractQuotationRelation");
        assertThat(QueryScope.CONTRACT_FIELD.getTargetEntity()).isEqualTo("ContractField");
        assertThat(QueryScope.CONTRACT_FORM.getTargetEntity()).isEqualTo("ContractForm");
        assertThat(QueryScope.CONTRACT_CONFIG.getTargetEntity()).isEqualTo("ContractConfig");
        assertThat(QueryScope.BUDGET_BILL.getTargetEntity()).isEqualTo("BudgetBill");
        assertThat(QueryScope.SUB_ORDER.getTargetEntity()).isEqualTo("SubOrder");
    }

    // ── 枚举属性测试 ──────────────────────────────────────

    @Test
    void enumValues_shouldHaveCorrectValueAndDescription() {
        assertThat(QueryScope.DEFAULT.getValue()).isEqualTo("default");
        assertThat(QueryScope.DEFAULT.getDescription()).isNotEmpty();
        
        assertThat(QueryScope.LIST.getValue()).isEqualTo("list");
        assertThat(QueryScope.LIST.getDescription()).isNotEmpty();
        
        assertThat(QueryScope.CONTRACT.getValue()).isEqualTo("Contract");
        assertThat(QueryScope.CONTRACT.getDescription()).isEqualTo("展开到合同实体");
    }
}
