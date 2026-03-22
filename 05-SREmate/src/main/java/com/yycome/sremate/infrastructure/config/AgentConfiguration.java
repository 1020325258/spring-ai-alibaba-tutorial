package com.yycome.sremate.infrastructure.config;

import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import com.yycome.sremate.trigger.agent.OntologyQueryTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

/**
 * Agent配置类
 */
@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/sre-agent.md")
    private Resource sreAgentPrompt;

    /**
     * 创建SRE Agent，注入本体摘要到 system prompt
     */
    @Bean
    public ChatClient sreAgent(ChatClient.Builder builder, ToolCallbackProvider sreTools, EntityRegistry entityRegistry) throws Exception {
        String promptContent = sreAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
        String ontologySummary = entityRegistry.getSummaryForPrompt();
        String entitySummary = entityRegistry.getEntitySummaryForPrompt();
        String finalPrompt = promptContent
                .replace("{{ontology_summary}}", ontologySummary)
                .replace("{{entity_summary}}", entitySummary);

        return builder
                .defaultSystem(finalPrompt)
                .defaultToolCallbacks(sreTools)
                .build();
    }

    /**
     * 注册所有工具
     */
    @Bean
    public ToolCallbackProvider sreTools(OntologyQueryTool ontologyQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ontologyQueryTool)
                .build();
    }
}
