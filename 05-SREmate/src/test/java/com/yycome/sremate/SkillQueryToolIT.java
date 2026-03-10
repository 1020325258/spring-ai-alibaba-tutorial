package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skill 运维知识库查询集成测试
 * 验证 querySkills 和 listSkillCategories 工具的完整链路
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class SkillQueryToolIT {

    @Autowired
    private ChatClient sreAgent;

    private String ask(String question) {
        String response = sreAgent.prompt().user(question).call().content();
        System.out.println("=== 问题：" + question + " ===\n" + response + "\n");
        return response;
    }

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
        assertThat(response).doesNotContain("error");
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
