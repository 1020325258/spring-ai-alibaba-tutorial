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
            【运维知识库查询】问题诊断和运维咨询时使用。

            触发条件：用户描述技术问题（超时、报错、异常）或运维咨询

            参数：
            - queryType：diagnosis（诊断）/operations（运维）/knowledge（知识）
            - keywords：关键词，空格分隔

            示例：
            - "数据库连接超时" → queryType=diagnosis, keywords="数据库 连接 超时"
            - "如何重启服务" → queryType=operations, keywords="重启 服务" """)
    public String querySkills(String queryType, String keywords) {
        long start = System.currentTimeMillis();
        try {
            String result = skillService.querySkills(queryType, keywords);
            log.info("[TOOL] querySkills → {}ms, ok", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("[TOOL] querySkills → {}ms, error: {}", System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
    }

    /**
     * 列出所有Skills分类
     *
     * @return Skills分类列表
     */
    @Tool(description = "列出SRE运维知识库的所有分类")
    public String listSkillCategories() {
        long start = System.currentTimeMillis();
        String result = "可用的Skills分类：" + String.join(", ", skillService.listSkillCategories());
        log.info("[TOOL] listSkillCategories → {}ms, ok", System.currentTimeMillis() - start);
        return result;
    }
}
