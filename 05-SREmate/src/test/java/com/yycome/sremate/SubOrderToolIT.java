package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 子单信息查询集成测试
 */
class SubOrderToolIT extends BaseSREIT {

    private static final String HOME_ORDER_NO = "825123110000002753";
    private static final String QUOTATION_ORDER_NO = "GBILL260309110407580001";

    @Test
    void subOrderKeyword_shouldCallQuerySubOrderInfo() {
        ask("查询订单" + HOME_ORDER_NO + "的子单信息");

        assertToolCalled("querySubOrderInfo");
        assertAllToolsSuccess();
    }

    @Test
    void subOrderWithQuotation_shouldCallQuerySubOrderInfo() {
        ask(HOME_ORDER_NO + "下" + QUOTATION_ORDER_NO + "的子单信息");

        assertToolCalled("querySubOrderInfo");
        assertAllToolsSuccess();
    }
}
