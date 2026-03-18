package com.yycome.sremate.infrastructure.config;

import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import com.yycome.sremate.trigger.agent.KnowledgeQueryTool;
import com.yycome.sremate.trigger.agent.OntologyQueryTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        String finalPrompt = promptContent.replace("{{ontology_summary}}", ontologySummary);

        return builder
                .defaultSystem(finalPrompt)
                .defaultToolCallbacks(sreTools)
                .build();
    }

    /**
     * 注册所有工具
     * KnowledgeQueryTool 是可选的（依赖 Elasticsearch）
     */
    @Bean
    public ToolCallbackProvider sreTools(
            OntologyQueryTool ontologyQueryTool,
            @Autowired(required = false) KnowledgeQueryTool knowledgeQueryTool) {
        List<Object> tools = new ArrayList<>();
        tools.add(ontologyQueryTool);
        if (knowledgeQueryTool != null) {
            tools.add(knowledgeQueryTool);
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools.toArray())
                .build();
    }
}
