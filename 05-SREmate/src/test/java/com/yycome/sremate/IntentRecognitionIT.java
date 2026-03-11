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

    // ===== 编号格式识别测试 =====

    @Nested
    @DisplayName("编号格式识别")
    class IdFormatRecognition {

        @Test
        @DisplayName("C前缀合同编号应使用queryContractData")
        void contractCode_CPrefix_shouldUseContractTool() {
            ask(CONTRACT_CODE + "合同数据");

            assertToolCalled("queryContractData");
            assertToolNotCalled("queryContractsByOrderId");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("纯数字订单号应使用queryContractsByOrderId")
        void orderId_pureDigits_shouldUseOrderTool() {
            ask(ORDER_ID + "有哪些合同");

            assertToolCalled("queryContractsByOrderId");
            assertToolNotCalled("queryContractData");
            assertAllToolsSuccess();
        }
    }

    // ===== 关键词意图识别测试 =====

    @Nested
    @DisplayName("关键词意图识别")
    class KeywordRecognition {

        @Test
        @DisplayName("版式关键词应使用queryContractFormId")
        void keyword_formId_shouldUseFormIdTool() {
            ask(CONTRACT_CODE + "的版式form_id");

            assertToolCalled("queryContractFormId");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("子单关键词应使用querySubOrderInfo")
        void keyword_subOrder_shouldUseSubOrderTool() {
            ask(ORDER_ID + "的子单信息");

            assertToolCalled("querySubOrderInfo");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("签约人关键词应设置正确的dataType")
        void keyword_signer_shouldSetCorrectDataType() {
            ask(CONTRACT_CODE + "的签约人");

            assertToolCalled("queryContractData");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("节点关键词应设置正确的dataType")
        void keyword_node_shouldSetCorrectDataType() {
            ask(CONTRACT_CODE + "的合同节点");

            assertToolCalled("queryContractData");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("配置表关键词应使用queryContractConfig")
        void keyword_config_shouldUseConfigTool() {
            ask(CONTRACT_CODE + "的合同配置表");

            assertToolCalled("queryContractConfig");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("报价单关键词应使用queryBudgetBillList")
        void keyword_budgetBill_shouldUseBudgetBillTool() {
            ask(ORDER_ID + "的报价单");

            assertToolCalled("queryBudgetBillList");
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

    // ===== 运维诊断测试 =====

    @Nested
    @DisplayName("运维诊断")
    class DiagnosisTests {

        @Test
        @DisplayName("数据库超时应使用querySkills")
        void diagnosis_databaseTimeout_shouldUseSkills() {
            ask("数据库连接超时怎么排查");

            assertToolCalled("querySkills");
            assertAllToolsSuccess();
        }

        @Test
        @DisplayName("服务超时应使用querySkills")
        void diagnosis_serviceTimeout_shouldUseSkills() {
            ask("服务超时怎么处理");

            assertToolCalled("querySkills");
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
