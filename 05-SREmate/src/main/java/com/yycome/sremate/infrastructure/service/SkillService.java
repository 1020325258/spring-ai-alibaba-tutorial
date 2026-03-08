package com.yycome.sremate.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Skills文档服务
 * 负责读取和查询Skills文档，支持缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private static final String SKILLS_PATH = "classpath:skills/**/*.md";

    private final CacheService cacheService;

    public String querySkills(String queryType, String keywords) {
        log.info("查询Skills文档 - 类型: {}, 关键词: {}", queryType, keywords);
        String cacheKey = buildCacheKey(queryType, keywords);
        return cacheService.getOrCompute(cacheKey, () -> doQuerySkills(queryType, keywords), Duration.ofMinutes(30));
    }

    private String doQuerySkills(String queryType, String keywords) {
        try {
            Resource[] resources = resourceResolver.getResources(SKILLS_PATH);

            List<Resource> filteredResources = Arrays.stream(resources)
                    .filter(resource -> {
                        if (!StringUtils.hasText(queryType)) return true;
                        try {
                            return resource.getURL().toString().contains("/" + queryType + "/");
                        } catch (IOException e) {
                            log.warn("Failed to get URL for resource: {}", resource.getFilename(), e);
                            return false;
                        }
                    })
                    .toList();

            List<String> matchedContents = new ArrayList<>();
            for (Resource resource : filteredResources) {
                String content = readFileContent(resource);
                if (containsKeywords(content, keywords)) {
                    matchedContents.add(content);
                }
            }

            if (matchedContents.isEmpty()) {
                return "未找到相关的排查经验文档。请尝试使用其他关键词查询，或者描述具体的问题现象。";
            }

            return String.join("\n\n---\n\n", matchedContents);

        } catch (IOException e) {
            log.error("读取Skills文档失败", e);
            return "读取Skills文档失败: " + e.getMessage();
        }
    }

    public List<String> listSkillCategories() {
        return Arrays.asList("diagnosis", "operations", "knowledge");
    }

    private String readFileContent(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private boolean containsKeywords(String content, String keywords) {
        if (!StringUtils.hasText(keywords)) return true;

        String lowerContent = content.toLowerCase();
        String[] keywordArray = keywords.toLowerCase().split("\\s+");

        for (String keyword : keywordArray) {
            if (lowerContent.contains(keyword)) return true;
        }

        return false;
    }

    private String buildCacheKey(String queryType, String keywords) {
        return String.format("skills:%s:%s",
                queryType != null ? queryType : "all",
                keywords != null ? keywords : "");
    }
}
