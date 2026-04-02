package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.config.node.QueryAgentNode;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.trigger.agent.OntologyQueryTool;
import com.yycome.sreagent.trigger.agent.ReadOntologyTool;
import com.yycome.sreagent.trigger.agent.ReadSkillTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SRE-Agent 配置类
 * 配置多 Agent 编排：queryAgent 和 investigateAgent 使用 ReactAgent，
 * supervisorAgent 保留作为 ChatClient（兼容 Studio 默认 ChatClient 路径）。
 */
@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/query-agent.md")
    private Resource queryAgentPrompt;

    @Value("classpath:prompts/investigate-agent.md")
    private Resource investigateAgentPrompt;

    /**
     * QueryAgentNode - 替代 AgentNode(queryAgent)，单次 LLM 调用 + 手动工具执行，跳过 ReAct 二次处理
     */
    @Bean
    public QueryAgentNode queryAgentNode(ChatModel chatModel,
                                          OntologyQueryTool ontologyQueryTool,
                                          EntityRegistry entityRegistry) throws Exception {
        String promptContent = queryAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
        String entitySummary = entityRegistry.getEntitySummaryForPrompt();
        String systemPrompt = promptContent.replace("{{entity_summary}}", entitySummary);
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(ontologyQueryTool)
                .build()
                .getToolCallbacks();
        return new QueryAgentNode(chatModel, systemPrompt, List.of(callbacks));
    }

    /**
     * Query Agent - ReactAgent，仅使用 ontologyQuery 工具
     */
    @Bean
    public ReactAgent queryAgent(ChatModel chatModel,
                                  OntologyQueryTool ontologyQueryTool,
                                  EntityRegistry entityRegistry) throws Exception {
        String promptContent = queryAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
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

            ## 意图不明确时的处理
            当用户的输入意图不明确时（如只提供了编号和症状描述，但没有明确说想做什么），请：
            1. 告知用户你识别到的不明确点
            2. 列出你可以帮助的能力：
               - **数据查询**：查询订单、合同、节点、报价等业务数据
               - **问题排查**：排查已知业务异常（如"弹窗提示请先完成报价"）
               - **系统配置**：查看本体模型、实体列表、环境信息等
            3. 引导用户明确表达需求

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
        String promptContent = queryAgentPrompt.getContentAsString(StandardCharsets.UTF_8);
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
        StringBuilder sb = new StringBuilder("## Available Skills\n\n");
        skillRegistry.listAll().forEach(skill ->
                sb.append("- ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n")
        );
        return sb.toString();
    }
}
