package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 子单信息查询集成测试
 * querySubOrderInfo 通过 HTTP 接口查询，验证 HTTP 链路正常
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SubOrderToolIT {

    // ===================== 修改为本地实际存在的测试数据 =====================
    private static final String HOME_ORDER_NO = "826030611000000795";
    private static final String QUOTATION_ORDER_NO = "GBILL260309110407580001";
    // ======================================================================

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

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
