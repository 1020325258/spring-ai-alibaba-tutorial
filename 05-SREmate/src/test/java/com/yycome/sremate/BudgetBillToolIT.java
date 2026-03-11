package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 报价单查询工具集成测试
 */
class BudgetBillToolIT extends BaseSREIT {

    private static final String PROJECT_ORDER_ID = "826031111000001859";

    @Test
    void budgetBillKeyword_shouldCallQueryBudgetBillList() {
        ask(PROJECT_ORDER_ID + "的报价单");

        assertToolCalled("queryBudgetBillList");
        assertAllToolsSuccess();
    }

    @Test
    void budgetBillNaturalLanguage_shouldCallQueryBudgetBillList() {
        ask("查询" + PROJECT_ORDER_ID + "的报价单列表");

        assertToolCalled("queryBudgetBillList");
        assertAllToolsSuccess();
    }
}
