package com.yycome.sreagent.config.node.routing;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 LLM Prompt 的意图路由策略。
 * 从 SkillRegistry 动态读取 Skill 列表，构建路由 Prompt，由 LLM 返回路由目标。
 */
public class LlmSkillRoutingStrategy implements SkillRoutingStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmSkillRoutingStrategy.class);

    private static final Set<String> BUILTIN_TARGETS = Set.of("query", "admin", "unclear");

    private final ChatModel chatModel;
    private final String routerPrompt;
    private final Set<String> skillNames;

    public LlmSkillRoutingStrategy(ChatModel chatModel, SkillRegistry skillRegistry) {
        this.chatModel = chatModel;
        List<SkillMetadata> skills = skillRegistry.listAll();
        this.skillNames = skills.stream().map(SkillMetadata::getName).collect(Collectors.toSet());
        this.routerPrompt = buildPrompt(skills);
        log.info("LlmSkillRoutingStrategy 初始化，已加载 {} 个 Skill: {}", skills.size(), skillNames);
    }

    @Override
    public String route(String userInput) {
        String fullPrompt = String.format(routerPrompt, userInput);
        var response = chatModel.call(new Prompt(fullPrompt));
        String result = response.getResult().getOutput().getText().trim().toLowerCase();
        log.info("LlmSkillRoutingStrategy 路由结果: {}", result);
        return result;
    }

    /**
     * 判断路由结果是否为已注册的 Skill name（而非内置目标）
     */
    public boolean isSkillName(String routeResult) {
        return skillNames.contains(routeResult);
    }

    private String buildPrompt(List<SkillMetadata> skills) {
        StringBuilder skillSection = new StringBuilder();
        if (skills.isEmpty()) {
            skillSection.append("（暂无排查 Skill）\n");
        } else {
            for (SkillMetadata skill : skills) {
                skillSection.append("- ").append(skill.getName())
                        .append("：").append(skill.getDescription()).append("\n");
            }
        }

        return """
                你是一个路由器，根据用户问题判断应该路由到哪个处理器。

                可用的排查 Skill（用户描述匹配时直接回复 Skill name）：
                """ + skillSection + """

                其他路由规则：
                - 用户想查询数据（订单、合同、节点、报价等）→ 只回复 "query"
                - 用户询问系统配置、本体模型、实体列表、环境信息，或需要回答"怎么做"、"怎么配置"等操作性问题 → 只回复 "admin"
                - 用户只提供了编号和症状描述，无法判断想做查询还是排查 → 只回复 "unclear"

                用户问题: %s

                注意：只回复一个单词（Skill name 或 query/admin/unclear），不要其他文字。
                """;
    }
}
