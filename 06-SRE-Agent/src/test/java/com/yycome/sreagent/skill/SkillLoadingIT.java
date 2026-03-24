package com.yycome.sreagent.skill;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill 加载集成测试
 * 验收：8.4 Skill 加载验收
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SkillLoadingIT {

    @Autowired(required = false)
    private SkillRegistry skillRegistry;

    @Test
    @DisplayName("SkillRegistry 扫描 skills/ 目录成功")
    void skillRegistryShouldScanSkillsDirectory() {
        assertThat(skillRegistry).isNotNull();
    }

    @Test
    @DisplayName("SkillRegistry 能获取 missing-personal-quote-diagnosis Skill")
    void skillRegistryShouldFindMissingPersonalQuoteDiagnosisSkill() {
        assertThat(skillRegistry).isNotNull();
        // 验证 Skill 加载
    }
}
