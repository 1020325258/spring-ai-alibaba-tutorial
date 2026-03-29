package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Skill 框架配置
 * 配置 SkillRegistry 扫描 skills/ 目录
 */
@Configuration
public class SkillConfiguration {

    @Bean
    public SkillRegistry skillRegistry(ResourceLoader resourceLoader) {
        Resource skillsResource = resourceLoader.getResource("classpath:skills");
        return FileSystemSkillRegistry.builder()
                .projectSkillsDirectory(skillsResource)
                .build();
    }
}
