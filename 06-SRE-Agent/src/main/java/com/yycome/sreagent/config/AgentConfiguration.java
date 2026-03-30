package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.trigger.agent.OntologyQueryTool;
import com.yycome.sreagent.trigger.agent.ReadOntologyTool;
import com.yycome.sreagent.trigger.agent.ReadSkillTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

/**
 * SRE-Agent 配置类
 * 配置多 Agent 编排：queryAgent 和 investigateAgent 使用 ReactAgent，
 * supervisorAgent 保留作为 ChatClient（兼容 Studio 默认 ChatClient 路径）。
 */
@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/sre-agent.md")
    private Resource sreAgentPrompt;

    @Value("classpath:prompts/investigate-agent.md")
    private Resource investigateAgentPrompt;

    /**
     * Query Agent - ReactAgent，仅使用 ontologyQuery 工具
     */
    @Bean
    public ReactAgent queryAgent(ChatModel chatModel,
                                  OntologyQueryTool ontologyQueryTool,
                                  EntityRegistry entityRegistry) throws Exception {
        String promptContent = sreAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
        String entitySummary = entityRegistry.getEntitySummaryForPrompt();
        String systemPrompt = promptContent.replace("{{entity_summary}}", entitySummary);

        return ReactAgent.builder()
                .name("queryAgent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(ontologyQueryTool)
                .build();
    }

    /**
     * Investigate Agent - ReactAgent，使用 ontologyQuery + readSkill 工具
     */
    @Bean
    public ReactAgent investigateAgent(ChatModel chatModel,
                                        OntologyQueryTool ontologyQueryTool,
                                        ReadSkillTool readSkillTool,
                                        SkillRegistry skillRegistry) throws Exception {
        String promptContent = investigateAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
        String skillsList = buildSkillsList(skillRegistry);
        String systemPrompt = promptContent + "\n\n" + skillsList;

        return ReactAgent.builder()
                .name("investigateAgent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(ontologyQueryTool, readSkillTool)
                .build();
    }

    /**
     * 注册所有工具（供 supervisorAgent ChatClient 使用）
     */
    @Bean
    public ToolCallbackProvider sreTools(OntologyQueryTool ontologyQueryTool,
                                          ReadSkillTool readSkillTool,
                                          ReadOntologyTool readOntologyTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ontologyQueryTool, readSkillTool, readOntologyTool)
                .build();
    }

    /**
     * Admin Agent - ReactAgent，使用 readOntology 和 readSkill 工具
     * 处理用户对系统配置、本体模型等信息的询问
     */
    @Bean
    public ReactAgent adminAgent(ChatModel chatModel,
                                   ReadOntologyTool readOntologyTool,
                                   ReadSkillTool readSkillTool,
                                   EntityRegistry entityRegistry,
                                   SkillRegistry skillRegistry) throws Exception {
        String entitySummary = entityRegistry.getEntitySummaryForPrompt();
        String skillsList = buildSkillsList(skillRegistry);
        String systemPrompt = """
            你是一个管理后台 Agent，负责回答用户关于系统配置和本体模型的问题。

            ## 本体模型
            当用户询问"本体模型有哪些"、"实体列表"等问题时，使用 readOntologyTool 工具查询。

            """ + entitySummary + "\n\n" + skillsList;

        return ReactAgent.builder()
                .name("adminAgent")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(readOntologyTool, readSkillTool)
                .build();
    }

    /**
     * Supervisor ChatClient - 保留用于兼容，@Primary 确保默认 ChatClient 注入不冲突
     */
    @Bean
    @Primary
    public ChatClient supervisorAgent(ChatClient.Builder builder,
                                      ToolCallbackProvider tools,
                                      SkillRegistry skillRegistry,
                                      EntityRegistry entityRegistry) throws Exception {
        String promptContent = sreAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
        String entitySummary = entityRegistry.getEntitySummaryForPrompt();
        String promptWithEntities = promptContent.replace("{{entity_summary}}", entitySummary);
        String skillsList = buildSkillsList(skillRegistry);
        String fullPrompt = promptWithEntities + "\n\n" + skillsList;

        return builder
                .defaultSystem(fullPrompt)
                .defaultToolCallbacks(tools)
                .build();
    }

    private String buildSkillsList(SkillRegistry skillRegistry) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Available Skills\n\n");
        sb.append("- sales-contract-sign-dialog-diagnosis: 排查销售/正签合同弹窗提示\"请先完成报价\"的原因\n");
        return sb.toString();
    }
}
