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
        // 验证返回了接口相关信息
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("接口"),
                r -> assertThat(r).containsIgnoringCase("sign-order"),
                r -> assertThat(r).containsIgnoringCase("contract"),
                r -> assertThat(r).containsIgnoringCase("health"),
                r -> assertThat(r).containsIgnoringCase("暂无"),
                r -> assertThat(r).containsIgnoringCase("分类")
        );
    }

    @Test
    void listAvailableEndpoints_byCategory_shouldFilterCorrectly() {
        String response = ask("查看 contract 分类的接口");

        assertThat(response).isNotBlank();
        // 验证返回了 contract 相关信息
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("contract"),
                r -> assertThat(r).containsIgnoringCase("子单"),
                r -> assertThat(r).containsIgnoringCase("版式"),
                r -> assertThat(r).containsIgnoringCase("暂无"),
                r -> assertThat(r).containsIgnoringCase("分类")
        );
    }
}
