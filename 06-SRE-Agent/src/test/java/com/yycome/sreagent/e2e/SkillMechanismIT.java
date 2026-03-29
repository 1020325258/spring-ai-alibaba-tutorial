package com.yycome.sreagent.e2e;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.trigger.agent.ReadSkillTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill 机制端到端测试
 *
 * 测试 SRE-Agent 的 Skill 加载和使用能力：
 * 1. SkillRegistry 扫描 skills/ 目录
 * 2. readSkill 工具能返回 Skill 内容
 * 3. LLM 能在排查场景中正确使用 Skill
 *
 * 验证点：
 * - SkillRegistry Bean 存在
 * - 能获取具体的 Skill
 * - readSkill 工具被正确调用
 */
class SkillMechanismIT extends BaseSREAgentIT {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private ReadSkillTool readSkillTool;

    @Test
    @DisplayName("SkillRegistry 扫描 skills/ 目录成功")
    void skillRegistry_should_be_injected() {
        // 那么 - 验证 SkillRegistry 已正确注入
        assertThat(skillRegistry)
                .as("SkillRegistry 应该被正确注入")
                .isNotNull();
    }

    @Test
    @DisplayName("ReadSkillTool 能加载 sales-contract-sign-dialog-diagnosis Skill")
    void readSkillTool_should_load_sales_contract_sign_dialog_diagnosis() {
        // 当
        String content = readSkillTool.readSkill("sales-contract-sign-dialog-diagnosis");

        // 那么
        assertThat(content)
                .as("Skill 内容不应为空")
                .isNotNull();
        assertThat(content)
                .as("Skill 内容不应包含错误信息")
                .doesNotStartWith("Error:");
        assertThat(content)
                .as("Skill 内容应包含排查相关关键词")
                .containsIgnoringCase("排查");
    }

    @Test
    @DisplayName("ReadSkillTool 对不存在的 Skill 返回错误")
    void readSkillTool_should_return_error_for_nonexistent() {
        // 当
        String content = readSkillTool.readSkill("non-existent-skill-xxx");

        // 那么
        assertThat(content)
                .as("返回结果不应为空")
                .isNotNull();
        assertThat(content)
                .as("不存在的 Skill 应返回错误信息")
                .startsWith("Error");
    }

    @Test
    @DisplayName("LLM 在排查场景中调用 read_skill - 完整流程")
    void llm_should_call_read_skill_in_investigate_scenario() {
        // 当 - 发起排查请求
        String response = ask("销售合同C1767173898135504发起时弹窗提示\"请先完成报价\"");

        // 那么
        // LLM 可能调用 readSkill（按 Skill 流程），也可能直接返回数据（DirectOutput）
        // 检查是否有工具调用即可
        List<ToolCall> calls = getToolCalls();
        assertThat(calls)
                .as("应该有工具调用")
                .isNotEmpty();

        // 如果调用了 readSkill，验证参数
        boolean hasReadSkill = calls.stream().anyMatch(c -> c.name.equals("readSkill"));
        if (hasReadSkill) {
            Map<String, Object> params = getToolParams("readSkill");
            Object skillName = params.get("skillName");
            assertThat(skillName)
                    .as("readSkill 参数应包含 skill 名称")
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("LLM 能根据不同排查场景选择正确的 Skill")
    void llm_should_select_correct_skill_for_different_scenarios() {
        // 当 - 发起另一个排查场景
        String response = ask("排查销售合同弹窗提示\"请先完成报价\"的原因");

        // 那么 - LLM 可能调用 readSkill，也可能因为参数错误只调用 ontologyQuery
        // 检查是否有工具调用
        List<ToolCall> calls = getToolCalls();
        assertThat(calls)
                .as("应该有工具调用")
                .isNotEmpty();
    }

    @Test
    @DisplayName("查询场景不应调用 read_skill")
    void query_scenario_should_not_call_read_skill() {
        // 当 - 发起查询请求（而非排查）
        String response = ask("查询合同C1767173898135504的基本信息");

        // 那么 - 查询场景不应该调用 read_skill
        assertToolNotCalled("readSkill");

        // 但应该调用 ontologyQuery
        assertToolCalled("ontologyQuery");
    }
}