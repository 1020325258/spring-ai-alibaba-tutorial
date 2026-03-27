package com.yycome.sreagent.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 问题排查能力端到端测试
 *
 * 测试 SRE-Agent 的问题排查能力：
 * 1. 排查合同发起时缺少个性化报价的原因
 * 2. 排查销售/正签合同弹窗提示"请先完成报价"的原因
 *
 * 验证点：
 * - LLM 能正确识别排查意图
 * - 调用 read_skill 加载对应的 Skill
 * - 调用 ontologyQuery 获取排查所需数据
 * - 输出排查结论
 */
class InvestigateAgentIT extends BaseSREAgentIT {

    @Test
    @DisplayName("排查场景 - 验证 ontologyQuery 参数正确性及输出为排查结论")
    void investigate_should_pass_correct_params() {
        // 当
        String response = ask("排查825123110000002753订单的个性化报价问题");

        // 那么
        // 1. 应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");

        // 2. 验证参数正确性：订单号应以 Order 为 entity
        Map<String, Object> params = getToolParams("ontologyQuery");
        Object entity = params.get("entity");
        assertThat(entity)
                .as("entity 参数应该是 Order（订单号是纯数字）")
                .isEqualTo("Order");

        // 3. 输出质量：LLM-as-Judge 验证输出是排查结论而非原始 JSON
        assertOutputIsInvestigationConclusion(response);
    }
}
