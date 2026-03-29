package com.yycome.sreagent.trigger.agent;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Skill 读取工具
 * 允许 Agent 读取完整的 Skill 内容
 */
@Component
public class ReadSkillTool {

    private final Logger logger = LoggerFactory.getLogger(ReadSkillTool.class);
    private final SkillRegistry skillRegistry;

    public ReadSkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Tool(description = """
        Reads the full content of a skill from the SkillRegistry.

        Usage:
        - The skill_name parameter must match the name of the skill as registered in the registry
        - The tool returns the full content of the skill file (e.g., SKILL.md) without frontmatter
        - If the skill is not found, an error will be returned

        Example:
        - read_skill("sales-contract-sign-dialog-diagnosis")
        """)
    public String readSkill(
            @ToolParam(description = "The name of the skill to read, must match one of the names in the Available Skills list") String skillName) {

        logger.info("ReadSkillTool 读取 Skill: {}", skillName);

        if (skillName == null || skillName.isEmpty()) {
            return "Error: skill_name is required";
        }

        try {
            String content = skillRegistry.readSkillContent(skillName);
            logger.info("ReadSkillTool 读取成功: {}, 内容长度: {}", skillName, content.length());
            return content;
        } catch (IllegalArgumentException e) {
            logger.warn("ReadSkillTool 参数错误: {}", e.getMessage());
            return "Error: " + e.getMessage();
        } catch (IllegalStateException e) {
            logger.warn("ReadSkillTool Skill 不存在: {}", e.getMessage());
            return "Error: Skill not found: " + skillName;
        } catch (Exception e) {
            logger.error("ReadSkillTool 读取失败: {}", e.getMessage());
            return "Error reading skill: " + e.getMessage();
        }
    }
}
