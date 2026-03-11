package com.yycome.sremate.domain.intent.service;

import com.yycome.sremate.domain.intent.model.PreprocessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图预处理器单元测试
 */
class IntentPreprocessorTest {

    private IntentPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new IntentPreprocessor();
    }

    // ===== 合同编号识别测试 =====

    @Test
    @DisplayName("识别标准合同编号 C1767173898135504")
    void extractContractCode_standard() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504合同数据");

        assertThat(result.hasContractCode()).isTrue();
        assertThat(result.getFirstContractCode()).isEqualTo("C1767173898135504");
        assertThat(result.hasOrderId()).isFalse();
    }

    @Test
    @DisplayName("识别小写合同编号 c1767173898135504")
    void extractContractCode_lowercase() {
        PreprocessResult result = preprocessor.preprocess("c1767173898135504合同");

        assertThat(result.hasContractCode()).isTrue();
        assertThat(result.getFirstContractCode()).isEqualTo("C1767173898135504");
    }

    @Test
    @DisplayName("识别多个合同编号")
    void extractContractCode_multiple() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504和C1767173898135505的合同");

        assertThat(result.getContractCodes()).hasSize(2);
        assertThat(result.getContractCodes()).containsExactly("C1767173898135504", "C1767173898135505");
    }

    // ===== 订单号识别测试 =====

    @Test
    @DisplayName("识别标准订单号 825123110000002753")
    void extractOrderId_standard() {
        PreprocessResult result = preprocessor.preprocess("825123110000002753的合同");

        assertThat(result.hasOrderId()).isTrue();
        assertThat(result.getFirstOrderId()).isEqualTo("825123110000002753");
        assertThat(result.hasContractCode()).isFalse();
    }

    @Test
    @DisplayName("区分合同编号和订单号")
    void distinguishContractCodeAndOrderId() {
        // 混合输入，应正确区分
        PreprocessResult result = preprocessor.preprocess("查C1767173898135504，不是825123110000002753");

        assertThat(result.hasContractCode()).isTrue();
        assertThat(result.hasOrderId()).isTrue();
        assertThat(result.getFirstContractCode()).isEqualTo("C1767173898135504");
        assertThat(result.getFirstOrderId()).isEqualTo("825123110000002753");
    }

    @Test
    @DisplayName("不把合同编号的数字部分当作订单号")
    void doNotExtractDigitsFromContractCode() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504合同");

        assertThat(result.hasContractCode()).isTrue();
        assertThat(result.hasOrderId()).isFalse();
    }

    // ===== 关键词识别测试 =====

    @Test
    @DisplayName("识别版式关键词")
    void extractKeyword_formId() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的版式form_id");

        assertThat(result.hasKeyword("FORM_ID")).isTrue();
    }

    @Test
    @DisplayName("识别子单关键词")
    void extractKeyword_subOrder() {
        PreprocessResult result = preprocessor.preprocess("825123110000002753的子单信息");

        assertThat(result.hasKeyword("SUB_ORDER")).isTrue();
    }

    @Test
    @DisplayName("识别合同节点关键词")
    void extractKeyword_node() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的合同节点");

        assertThat(result.hasKeyword("CONTRACT_NODE")).isTrue();
    }

    @Test
    @DisplayName("识别签约人关键词")
    void extractKeyword_user() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的签约人信息");

        assertThat(result.hasKeyword("CONTRACT_USER")).isTrue();
    }

    @Test
    @DisplayName("识别运维诊断关键词")
    void extractKeyword_diagnosis() {
        PreprocessResult result = preprocessor.preprocess("数据库连接超时怎么办");

        assertThat(result.hasKeyword("DIAGNOSIS")).isTrue();
    }

    // ===== 工具推荐测试 =====

    @Test
    @DisplayName("推荐 queryContractFormId 工具")
    void recommendTool_formId() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的版式数据");

        assertThat(result.getRecommendedTool()).isEqualTo("queryContractFormId");
    }

    @Test
    @DisplayName("推荐 querySubOrderInfo 工具")
    void recommendTool_subOrder() {
        PreprocessResult result = preprocessor.preprocess("825123110000002753的子单");

        assertThat(result.getRecommendedTool()).isEqualTo("querySubOrderInfo");
    }

    @Test
    @DisplayName("推荐 queryContractData 工具（C前缀）")
    void recommendTool_contractData() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的合同数据");

        assertThat(result.getRecommendedTool()).isEqualTo("queryContractData");
        assertThat(result.getRecommendedDataType()).isEqualTo("ALL");
    }

    @Test
    @DisplayName("推荐 queryContractData 工具并设置正确 dataType（节点）")
    void recommendTool_contractData_node() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的合同节点");

        assertThat(result.getRecommendedTool()).isEqualTo("queryContractData");
        assertThat(result.getRecommendedDataType()).isEqualTo("CONTRACT_NODE");
    }

    @Test
    @DisplayName("推荐 queryContractsByOrderId 工具（纯数字订单号）")
    void recommendTool_contractsByOrderId() {
        PreprocessResult result = preprocessor.preprocess("825123110000002753的合同");

        assertThat(result.getRecommendedTool()).isEqualTo("queryContractsByOrderId");
    }

    @Test
    @DisplayName("推荐 querySkills 工具（运维诊断）")
    void recommendTool_skills() {
        PreprocessResult result = preprocessor.preprocess("数据库连接超时怎么办");

        assertThat(result.getRecommendedTool()).isEqualTo("querySkills");
    }

    @Test
    @DisplayName("无明确编号时不推荐工具")
    void recommendTool_none() {
        PreprocessResult result = preprocessor.preprocess("你好");

        assertThat(result.getRecommendedTool()).isNull();
    }

    // ===== 边界情况测试 =====

    @Test
    @DisplayName("空输入处理")
    void emptyInput() {
        PreprocessResult result = preprocessor.preprocess("");

        assertThat(result.getContractCodes()).isEmpty();
        assertThat(result.getOrderIds()).isEmpty();
        assertThat(result.getRecommendedTool()).isNull();
    }

    @Test
    @DisplayName("特殊字符输入处理")
    void specialCharacters() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504!!!@#$%合同");

        assertThat(result.hasContractCode()).isTrue();
    }

    // ===== 预处理摘要测试 =====

    @Test
    @DisplayName("生成预处理摘要")
    void toSummary() {
        PreprocessResult result = preprocessor.preprocess("C1767173898135504的合同数据");

        String summary = result.toSummary();

        assertThat(summary).contains("识别到的合同编号");
        assertThat(summary).contains("C1767173898135504");
        assertThat(summary).contains("推荐工具");
        assertThat(summary).contains("queryContractData");
    }
}
