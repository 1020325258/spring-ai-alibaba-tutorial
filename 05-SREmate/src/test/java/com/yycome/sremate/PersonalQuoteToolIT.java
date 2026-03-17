package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 个性化报价查询集成测试
 * 通过 OntologyQueryTool.queryPersonalQuote 方法实现
 */
class PersonalQuoteToolIT extends BaseSREIT {

    private static final String ORDER_ID = "826031210000003581";
    private static final String SUB_ORDER_NO = "S15260312120004471";
    private static final String BILL_CODE = "GBILL260312104241050001";

    @Test
    void personalQuoteKeyword_withSubOrder_shouldCallQueryPersonalQuote() {
        ask(ORDER_ID + "下" + SUB_ORDER_NO + "的个性化报价");

        assertToolCalled("queryPersonalQuote");
        assertAllToolsSuccess();
    }

    @Test
    void personalQuoteKeyword_withBillCode_shouldCallQueryPersonalQuote() {
        ask(ORDER_ID + "下" + BILL_CODE + "的个性化报价");

        assertToolCalled("queryPersonalQuote");
        assertAllToolsSuccess();
    }

    @Test
    void personalQuoteKeyword_shouldNotCallBudgetBillTool() {
        ask(ORDER_ID + "下" + SUB_ORDER_NO + "的个性化报价");

        assertToolNotCalled("queryBudgetBillList");
        assertToolCalled("queryPersonalQuote");
        assertAllToolsSuccess();
    }
}
