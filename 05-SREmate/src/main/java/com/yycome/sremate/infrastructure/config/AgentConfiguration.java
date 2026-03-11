package com.yycome.sremate.infrastructure.config;

import com.yycome.sremate.trigger.agent.BudgetBillTool;
import com.yycome.sremate.trigger.agent.ContractQueryTool;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
import com.yycome.sremate.trigger.agent.KnowledgeQueryTool;
import com.yycome.sremate.trigger.agent.SkillQueryTool;
import com.yycome.sremate.trigger.agent.SubOrderTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

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
     * 创建SRE Agent
     */
    @Bean
    public ChatClient sreAgent(ChatClient.Builder builder, ToolCallbackProvider sreTools) {
        return builder
                .defaultSystem(sreAgentPrompt)
                .defaultToolCallbacks(sreTools)
                .build();
    }

    /**
     * 注册所有工具
     * KnowledgeQueryTool 是可选的（依赖 Elasticsearch）
     */
    @Bean
    public ToolCallbackProvider sreTools(
            SkillQueryTool skillQueryTool,
            ContractQueryTool contractQueryTool,
            BudgetBillTool budgetBillTool,
            SubOrderTool subOrderTool,
            HttpEndpointTool httpEndpointTool,
            @Autowired(required = false) KnowledgeQueryTool knowledgeQueryTool) {
        List<Object> tools = new ArrayList<>();
        tools.add(skillQueryTool);
        tools.add(contractQueryTool);
        tools.add(budgetBillTool);
        tools.add(subOrderTool);
        tools.add(httpEndpointTool);
        if (knowledgeQueryTool != null) {
            tools.add(knowledgeQueryTool);
        }
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools.toArray())
                .build();
    }
}
