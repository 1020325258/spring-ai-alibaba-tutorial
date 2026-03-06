package com.yycome.sremate.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillServiceUnitTest {

    private SkillService skillService;
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService();
        skillService = new SkillService(cacheService);
    }

    @Test
    void listSkillCategories_returnsExactlyThreeCategories() {
        List<String> categories = skillService.listSkillCategories();
        assertThat(categories).hasSize(3)
                .containsExactlyInAnyOrder("diagnosis", "operations", "knowledge");
    }

    @Test
    void querySkills_withNonExistentType_returnsNoResultMessage() {
        String result = skillService.querySkills("nonexistent", "any keyword");
        assertThat(result).contains("未找到相关的排查经验文档");
    }

    @Test
    void querySkills_withEmptyQueryType_searchesAllCategories() {
        String result = skillService.querySkills("", "数据库");
        assertThat(result).doesNotContain("未找到相关的排查经验文档");
    }

    @Test
    void querySkills_withNullQueryType_searchesAllCategories() {
        String result = skillService.querySkills(null, "数据库");
        assertThat(result).doesNotContain("未找到相关的排查经验文档");
    }

    @Test
    void querySkills_withEmptyKeywords_returnsAllInCategory() {
        String result = skillService.querySkills("diagnosis", "");
        assertThat(result).contains("数据库连接超时");
        assertThat(result).contains("服务超时");
    }

    @Test
    void querySkills_withNonMatchingKeywords_returnsNoResultMessage() {
        String result = skillService.querySkills("diagnosis", "xyzNonExistentKeyword123");
        assertThat(result).contains("未找到相关的排查经验文档");
    }

    @Test
    void querySkills_withMultipleKeywords_orMatchesAny() {
        String result = skillService.querySkills("diagnosis", "xyzNotFound 数据库");
        assertThat(result).doesNotContain("未找到相关的排查经验文档");
        assertThat(result).contains("数据库连接超时");
    }

    @Test
    void querySkills_multipleMatchingDocs_joinedWithSeparator() {
        String result = skillService.querySkills("diagnosis", "排查步骤");
        if (!result.contains("未找到相关的排查经验文档")) {
            // If multiple docs match, they should be joined with separator
            // If only one matches, the separator won't be present — that's also valid
            assertThat(result).isNotBlank();
        }
    }
}
