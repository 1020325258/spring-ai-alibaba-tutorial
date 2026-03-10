package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 预定义接口工具集成测试
 * 主要验证 listAvailableEndpoints 和 YAML 模板加载是否正常
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class HttpEndpointToolIT {

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

    @Test
    void listAvailableEndpoints_shouldReturnEndpointList() {
        String response = ask("有哪些可用的预定义接口");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("error");
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("sign-order-list"),
                r -> assertThat(r).containsIgnoringCase("contract-form-data"),
                r -> assertThat(r).containsIgnoringCase("sub-order-info")
        );
    }

    @Test
    void listAvailableEndpoints_byCategory_shouldFilterCorrectly() {
        String response = ask("查看 contract 分类的接口");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("error");
    }
}
