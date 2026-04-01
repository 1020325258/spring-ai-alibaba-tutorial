package com.yycome.sreagent.e2e;

import org.junit.jupiter.api.Test;

/**
 * InvestigateAgent 端到端测试
 *
 * <p>验证问题排查路径：路由到 investigateAgent → readSkill → ontologyQuery → 四段式结论
 *
 * @see BaseSREAgentIT
 */
class InvestigateAgentIT extends BaseSREAgentIT {

    /**
     * 验证"排查XXX缺少定软电报价"能正确路由到 investigateAgent 并调用 readSkill。
     * 历史 bug：该描述因含"报价"被误路由到 queryAgent。
     *
     */
    @Test
    void investigate_missing_personal_quote_with_investigate_prefix() {
        ask("排查826033014000004927缺少定软电报价");

        // 第一层：路由正确 + readSkill 被调用
        assertToolCalled("readSkill");
        assertToolParamEquals("readSkill", "skillName", "sales-contract-sign-dialog-diagnosis");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    /**
     * 验证无"排查"关键词、仅描述症状时也能正确路由到 investigateAgent。
     *
     */
    @Test
    void investigate_missing_personal_quote_symptom_only() {
        ask("826033014000004927缺少定软电品类报价");

        assertToolCalled("readSkill");
        assertToolParamEquals("readSkill", "skillName", "sales-contract-sign-dialog-diagnosis");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }

    /**
     * 验证弹窗提示"请先完成报价"的经典排查场景。
     *
     */
    @Test
    void investigate_dialog_please_complete_quote() {
        ask("826033014000004927提示请先完成报价");

        assertToolCalled("readSkill");
        assertToolParamEquals("readSkill", "skillName", "sales-contract-sign-dialog-diagnosis");
        assertToolCalled("ontologyQuery");
        assertAllToolsSuccess();
    }
}
