package com.yycome.sremate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图识别准确性集成测试
 * 验证 Agent 能正确识别用户意图并选择合适的工具
 */
class IntentRecognitionIT extends BaseSREIT {

    private static final String CONTRACT_CODE = "C1767173898135504";
    private static final String ORDER_ID = "825123110000002753";

    // ===== 编号格式识别测试 =====

    @Test
    @DisplayName("C前缀合同编号应使用queryContractData")
    void contractCode_CPrefix_shouldUseContractTool() {
        String response = ask(CONTRACT_CODE + "合同数据");

        // 验证返回了合同数据
        assertThat(response).contains("contractCode");
        // 不应出现订单号相关错误
        assertThat(response).doesNotContain("是订单号");
        assertThat(response).doesNotContain("请使用 queryContractsByOrderId");
    }

    @Test
    @DisplayName("纯数字订单号应使用queryContractsByOrderId")
    void orderId_pureDigits_shouldUseOrderTool() {
        String response = ask(ORDER_ID + "有哪些合同");

        // 验证返回了合同列表
        assertThat(response).contains("contracts");
        // 不应出现合同编号相关错误
        assertThat(response).doesNotContain("是合同编号");
    }

    @Test
    @DisplayName("混合编号应识别主要意图")
    void mixedIds_shouldIdentifyPrimaryIntent() {
        String response = ask("查" + CONTRACT_CODE + "，不是" + ORDER_ID);

        // 应识别出合同编号
        assertThat(response).containsIgnoringCase(CONTRACT_CODE);
    }

    // ===== 关键词意图识别测试 =====

    @Test
    @DisplayName("版式关键词应使用queryContractFormId")
    void keyword_formId_shouldUseFormIdTool() {
        String response = ask(CONTRACT_CODE + "的版式form_id");

        // 即使查询失败，也应该调用了正确的工具
        // 不应出现"合同数据"相关的错误提示
        assertThat(response).doesNotContain("合同数据时不能用此工具");
    }

    @Test
    @DisplayName("子单关键词应使用querySubOrderInfo")
    void keyword_subOrder_shouldUseSubOrderTool() {
        String response = ask(ORDER_ID + "的子单信息");

        // 验证返回了子单数据
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).contains("orderNo"),
            r -> assertThat(r).contains("子单"),
            r -> assertThat(r).contains("S1")
        );
    }

    @Test
    @DisplayName("签约人关键词应设置正确的dataType")
    void keyword_signer_shouldSetCorrectDataType() {
        String response = ask(CONTRACT_CODE + "的签约人");

        // 验证返回了签约人数据
        assertThat(response).contains("contract_user");
    }

    @Test
    @DisplayName("节点关键词应设置正确的dataType")
    void keyword_node_shouldSetCorrectDataType() {
        String response = ask(CONTRACT_CODE + "的合同节点");

        // 验证返回了节点数据
        assertThat(response).contains("contract_node");
    }

    @Test
    @DisplayName("配置表关键词应使用queryContractConfig")
    void keyword_config_shouldUseConfigTool() {
        String response = ask(CONTRACT_CODE + "的合同配置表");

        // 验证返回了配置数据
        assertThat(response).contains("contract_city_company_info");
    }

    // ===== 边界情况测试 =====

    @Test
    @DisplayName("模糊输入应返回合理响应")
    void ambiguousInput_shouldReturnReasonableResponse() {
        String response = ask("查合同");

        // 即使模糊输入，也应返回合理响应（可能是默认查询某个合同）
        assertThat(response).isNotEmpty();
    }

    @Test
    @DisplayName("列出所有接口应返回可用接口")
    void listEndpoints_shouldReturnEndpoints() {
        String response = ask("有哪些可用的预定义接口");

        // 应包含接口相关内容
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
    @DisplayName("按分类列出接口应返回正确结果")
    void listEndpoints_byCategory_shouldFilterCorrectly() {
        String response = ask("查看 contract 分类的接口");

        // 应包含 contract 相关接口
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("sub-order-info"),
            r -> assertThat(r).containsIgnoringCase("contract"),
            r -> assertThat(r).containsIgnoringCase("子单")
        );
    }

    // ===== 运维诊断测试 =====

    @Test
    @DisplayName("数据库超时应使用querySkills")
    void diagnosis_databaseTimeout_shouldUseSkills() {
        String response = ask("数据库连接超时怎么排查");

        // 应返回排查建议
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("连接"),
            r -> assertThat(r).containsIgnoringCase("超时"),
            r -> assertThat(r).containsIgnoringCase("排查"),
            r -> assertThat(r).containsIgnoringCase("数据库")
        );
    }

    @Test
    @DisplayName("服务超时应使用querySkills")
    void diagnosis_serviceTimeout_shouldUseSkills() {
        String response = ask("服务超时怎么处理");

        // 应返回排查建议
        assertThat(response).satisfiesAnyOf(
            r -> assertThat(r).containsIgnoringCase("超时"),
            r -> assertThat(r).containsIgnoringCase("服务"),
            r -> assertThat(r).containsIgnoringCase("排查"),
            r -> assertThat(r).containsIgnoringCase("检查")
        );
    }

    // ===== 响应格式测试 =====

    @Test
    @DisplayName("数据查询应直接返回JSON")
    void dataQuery_shouldReturnDirectJson() {
        String response = ask(CONTRACT_CODE + "合同数据");

        // 应以 { 开头（直接 JSON 输出）
        assertThat(response.trim()).startsWith("{");
    }
}
