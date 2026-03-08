package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Skills文档查询工具（触发层）
 * 用于查询SRE运维知识库中的排查经验和解决方案
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillQueryTool {

    private final SkillService skillService;

    /**
     * 查询SRE运维知识库
     *
     * @param queryType 查询类型：diagnosis（问题诊断）、operations（运维咨询）、knowledge（通用知识）
     * @param keywords  关键词，用于匹配相关的排查经验
     * @return 相关的排查经验和解决方案
     */
    @Tool(description = """
            查询SRE运维知识库，获取问题排查经验和解决方案。
            queryType可选值：diagnosis（问题诊断）、operations（运维咨询）、knowledge（通用知识）。
            keywords用于匹配相关的文档，多个关键词用空格分隔。""")
    public String querySkills(String queryType, String keywords) {
        log.info("调用SkillQueryTool - 类型: {}, 关键词: {}", queryType, keywords);
        return skillService.querySkills(queryType, keywords);
    }

    /**
     * 列出所有Skills分类
     *
     * @return Skills分类列表
     */
    @Tool(description = "列出SRE运维知识库的所有分类")
    public String listSkillCategories() {
        log.info("调用listSkillCategories");
        return "可用的Skills分类：" + String.join(", ", skillService.listSkillCategories());
    }
}
