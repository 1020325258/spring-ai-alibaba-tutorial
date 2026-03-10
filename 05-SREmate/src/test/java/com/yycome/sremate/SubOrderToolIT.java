package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 子单信息查询集成测试
 */
class SubOrderToolIT extends BaseSREIT {

    private static final String HOME_ORDER_NO = "825123110000002753";
    private static final String QUOTATION_ORDER_NO = "GBILL260309110407580001";

    @Test
    void querySubOrderInfo_byOrderOnly_shouldReturnSubOrderData() {
        String response = ask("查询订单" + HOME_ORDER_NO + "的子单信息");

        assertThat(response).doesNotContain("接口调用失败");
        assertThat(response).doesNotContain("ConnectException");
    }

    @Test
    void querySubOrderInfo_byOrderAndQuotation_shouldReturnSubOrderData() {
        String response = ask(HOME_ORDER_NO + "下" + QUOTATION_ORDER_NO + "的子单信息");

        assertThat(response).doesNotContain("接口调用失败");
        assertThat(response).doesNotContain("ConnectException");
        assertThat(response).doesNotContain("参数验证失败");
    }
}
