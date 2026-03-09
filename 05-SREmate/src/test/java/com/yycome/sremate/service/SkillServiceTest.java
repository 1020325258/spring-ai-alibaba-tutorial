package com.yycome.sremate.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import com.yycome.sremate.infrastructure.service.SkillService;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.dashscope.api-key=test-dummy-key",
        "spring.datasource.sre.jdbc-url=jdbc:h2:mem:sretest;DB_CLOSE_DELAY=-1",
        "spring.datasource.sre.driver-class-name=org.h2.Driver",
        "spring.datasource.sre.username=sa",
        "spring.datasource.sre.password=",
        "sre.console.enabled=false",
        "spring.ai.vectorstore.elasticsearch.enabled=false",
        "knowledge.loader.enabled=false"
})
class SkillServiceTest {

    @MockBean
    ChatClient chatClient;

    @Autowired
    private SkillService skillService;

    @Test
    void querySkills_withDiagnosisTypeAndDatabaseKeyword_returnsMatchingContent() {
        String result = skillService.querySkills("diagnosis", "数据库 连接");
        assertThat(result).isNotNull();
        assertThat(result).contains("数据库连接超时");
    }

    @Test
    void listSkillCategories_containsAllThreeCategories() {
        var categories = skillService.listSkillCategories();
        assertThat(categories).isNotNull();
        assertThat(categories).containsExactlyInAnyOrder("diagnosis", "operations", "knowledge");
    }
}
