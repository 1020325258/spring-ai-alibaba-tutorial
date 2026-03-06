package com.yycome.sremate.service;

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
import java.util.stream.Collectors;

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

    /**
     * 根据关键词查询相关的Skills文档
     * 使用缓存提升性能
     *
     * @param queryType 查询类型（diagnosis/operations/knowledge）
     * @param keywords 关键词
     * @return 相关的Skills文档内容
     */
    public String querySkills(String queryType, String keywords) {
        log.info("查询Skills文档 - 类型: {}, 关键词: {}", queryType, keywords);

        // 构建缓存键
        String cacheKey = buildCacheKey(queryType, keywords);

        // 尝试从缓存获取
        return cacheService.getOrCompute(cacheKey, () -> {
            return doQuerySkills(queryType, keywords);
        }, Duration.ofMinutes(30));
    }

    /**
     * 实际查询Skills文档
     */
    private String doQuerySkills(String queryType, String keywords) {
        try {
            // 获取所有Skills文档
            Resource[] resources = resourceResolver.getResources(SKILLS_PATH);

            // 根据类型过滤
            List<Resource> filteredResources = Arrays.stream(resources)
                    .filter(resource -> {
                        if (!StringUtils.hasText(queryType)) {
                            return true;
                        }
                        try {
                            String filename = resource.getFilename();
                            return filename != null &&
                                    resource.getURL().toString().contains("/" + queryType + "/");
                        } catch (IOException e) {
                            log.warn("Failed to get URL for resource: {}", resource.getFilename(), e);
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            // 根据关键词匹配
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

            // 合并所有匹配的内容
            return String.join("\n\n---\n\n", matchedContents);

        } catch (IOException e) {
            log.error("读取Skills文档失败", e);
            return "读取Skills文档失败: " + e.getMessage();
        }
    }

    /**
     * 列出所有Skills分类
     */
    public List<String> listSkillCategories() {
        return Arrays.asList("diagnosis", "operations", "knowledge");
    }

    /**
     * 读取文件内容
     */
    private String readFileContent(Resource resource) throws IOException {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 检查内容是否包含关键词
     */
    private boolean containsKeywords(String content, String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return true;
        }

        String lowerContent = content.toLowerCase();
        String[] keywordArray = keywords.toLowerCase().split("\\s+");

        // 检查是否包含任意一个关键词
        for (String keyword : keywordArray) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 构建缓存键
     */
    private String buildCacheKey(String queryType, String keywords) {
        return String.format("skills:%s:%s",
                queryType != null ? queryType : "all",
                keywords != null ? keywords : "");
    }
}
