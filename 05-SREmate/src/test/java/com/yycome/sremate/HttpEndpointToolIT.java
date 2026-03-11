package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * HTTP 预定义接口工具集成测试
 */
class HttpEndpointToolIT extends BaseSREIT {

    @Test
    void listEndpointsKeyword_shouldCallListAvailableEndpoints() {
        ask("有哪些可用的预定义接口");

        assertToolCalled("listAvailableEndpoints");
        assertAllToolsSuccess();
    }

    @Test
    void listEndpointsByCategory_shouldCallListAvailableEndpoints() {
        ask("查看 contract 分类的接口");

        assertToolCalled("listAvailableEndpoints");
        assertAllToolsSuccess();
    }
}
