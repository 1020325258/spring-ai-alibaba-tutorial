package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.trigger.agent.OntologyQueryTool;
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
                                          ReadSkillTool readSkillTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ontologyQueryTool, readSkillTool)
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
