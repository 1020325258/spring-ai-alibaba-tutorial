package com.yycome.sremate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 意图识别准确性集成测试
 * 验证 Agent 能正确识别用户意图并选择合适的工具
 */
class IntentRecognitionIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String ORDER_ID = "825123110000002753";

    // ===== 本体论工具意图识别测试 =====

    @Nested
    @DisplayName("本体论工具意图识别")
    class OntologyToolRecognition {

        @Test
        @DisplayName("C前缀合同编号应使用ontologyQuery")
        void contractCode_CPrefix_shouldUseOntologyQuery() {
            ask(CONTRACT_CODE + "合同数据");

            assertToolCalled("ontologyQuery");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("纯数字订单号应使用ontologyQuery")
        void orderId_pureDigits_shouldUseOntologyQuery() {
            ask(ORDER_ID + "有哪些合同");

            assertToolCalled("ontologyQuery");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("版式关键词应使用ontologyQuery(queryScope=form)")
        void keyword_formId_shouldUseOntologyQuery() {
            ask(CONTRACT_CODE + "的版式form_id");

            assertToolCalled("ontologyQuery");
            assertToolNotCalled("queryContractFormId");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("配置表关键词应使用ontologyQuery(queryScope=config)")
        void keyword_config_shouldUseOntologyQuery() {
            ask(CONTRACT_CODE + "的合同配置表");

            assertToolCalled("ontologyQuery");
            assertToolNotCalled("queryContractConfig");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("报价单关键词应使用ontologyQuery")
        void keyword_budgetBill_shouldUseOntologyQuery() {
            ask(ORDER_ID + "的报价单");

            assertToolCalled("ontologyQuery");
            assertAllToolsSuccess();
        }
    }

    // ===== 关键词意图识别测试 =====

    @Nested
    @DisplayName("关键词意图识别")
    class KeywordRecognition {

        @Test
        @DisplayName("子单关键词应使用querySubOrderInfo")
        void keyword_subOrder_shouldUseSubOrderTool() {
            ask(ORDER_ID + "的子单信息");

            assertToolCalled("querySubOrderInfo");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("instance_id关键词应使用queryContractInstanceId")
        void keyword_instanceId_shouldUseInstanceIdTool() {
            ask(CONTRACT_CODE + "的instance_id");

            assertToolCalled("queryContractInstanceId");
            assertAllToolsSuccess();
        }
    }

    // ===== 接口查询测试 =====

    @Nested
    @DisplayName("接口查询")
    class EndpointTests {

        @Test
        @DisplayName("列出所有接口应使用listAvailableEndpoints")
        void listEndpoints_shouldUseListTool() {
            ask("有哪些可用的预定义接口");

            assertToolCalled("listAvailableEndpoints");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("按分类列出接口应使用listAvailableEndpoints")
        void listEndpoints_byCategory_shouldUseListTool() {
            ask("查看 contract 分类的接口");

            assertToolCalled("listAvailableEndpoints");
            assertAllToolsSuccess();
        }
    }
}
