package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报价单查询工具集成测试
 */
class BudgetBillToolIT extends BaseSREIT {

    private static final String PROJECT_ORDER_ID = "826031111000001859";

    @Test
    void queryBudgetBill_byOrderId_shouldReturnBillFieldsWithSubOrders() {
        String response = ask(PROJECT_ORDER_ID + "的报价单");

        assertThat(response).isNotBlank();
        // 报价单基本字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("billType"),
                r -> assertThat(r).containsIgnoringCase("billCode"),
                r -> assertThat(r).containsIgnoringCase("decorateBudgetList"),
                r -> assertThat(r).containsIgnoringCase("personalBudgetList")
        );
        // 聚合子单字段
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("subOrders"),
                r -> assertThat(r).containsIgnoringCase("orderNo"),
                r -> assertThat(r).containsIgnoringCase("dueAmount")
        );
        assertThat(response).doesNotContain("接口调用失败");
        assertThat(response).doesNotContain("未找到接口模板");
    }

    @Test
    void queryBudgetBill_naturalLanguage_shouldRecognizeIntent() {
        String response = ask("查询" + PROJECT_ORDER_ID + "的报价单列表");

        assertThat(response).isNotBlank();
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("billType"),
                r -> assertThat(r).containsIgnoringCase("billCode"),
                r -> assertThat(r).containsIgnoringCase("decorateBudgetList"),
                r -> assertThat(r).containsIgnoringCase("personalBudgetList"),
                r -> assertThat(r).containsIgnoringCase("报价")
        );
        assertThat(response).doesNotContain("接口调用失败");
    }
}
