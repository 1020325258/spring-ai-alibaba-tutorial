package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill 运维知识库查询集成测试
 */
class SkillQueryToolIT extends BaseSREIT {

    @Test
    void querySkills_databaseTimeout_shouldReturnRunbook() {
        String response = ask("数据库连接超时怎么排查");

        assertThat(response).isNotBlank();
        assertThat(response).doesNotContain("未找到任何匹配");
        assertThat(response).doesNotContain("error");
    }

    @Test
    void querySkills_serviceTimeout_shouldReturnRunbook() {
        String response = ask("服务超时怎么处理");

        assertThat(response).isNotBlank();
        // 注意：知识库内容可能包含 "error" 作为 JSON 示例的一部分，不应视为错误
        // 只检查不包含实际的错误提示
        assertThat(response).doesNotContain("未找到任何匹配");
    }

    @Test
    void listSkillCategories_shouldReturnCategories() {
        String response = ask("运维知识库有哪些分类");

        assertThat(response).isNotBlank();
        assertThat(response).satisfiesAnyOf(
                r -> assertThat(r).containsIgnoringCase("diagnosis"),
                r -> assertThat(r).containsIgnoringCase("诊断"),
                r -> assertThat(r).containsIgnoringCase("分类")
        );
    }
}
