package com.yycome.sreagent.trigger.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReadSkillTool 集成测试
 * 验收：8.4 Skill 加载验收 - read_skill 工具能返回 Skill 内容
 */
@SpringBootTest
@ActiveProfiles("local")
class ReadSkillToolIT {

    @Autowired
    private ReadSkillTool readSkillTool;

    @Test
    @DisplayName("read_skill 工具能返回 Skill 内容")
    void readSkillShouldReturnSkillContent() {
        // When
        String content = readSkillTool.readSkill("sales-contract-sign-dialog-diagnosis");

        // Then
        assertThat(content).isNotNull();
        assertThat(content).doesNotStartWith("Error:");
        assertThat(content).containsIgnoringCase("排查");  // Skill 内容应包含排查相关内容
    }

    @Test
    @DisplayName("read_skill 对不存在的 Skill 返回错误")
    void readSkillShouldReturnErrorForNonExistentSkill() {
        // When
        String content = readSkillTool.readSkill("non-existent-skill");

        // Then
        assertThat(content).isNotNull();
        assertThat(content).startsWith("Error");
    }

    @Test
    @DisplayName("read_skill 对空参数返回错误")
    void readSkillShouldReturnErrorForEmptyParam() {
        // When
        String content = readSkillTool.readSkill("");

        // Then
        assertThat(content).isNotNull();
        assertThat(content).startsWith("Error");
    }
}
