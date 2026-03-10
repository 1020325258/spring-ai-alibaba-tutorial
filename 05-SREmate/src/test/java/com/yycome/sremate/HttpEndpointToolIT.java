package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP 预定义接口工具集成测试
 */
class HttpEndpointToolIT extends BaseSREIT {

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
