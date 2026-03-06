package com.yycome.sremate.config;

import com.yycome.sremate.tools.HttpQueryTool;
import com.yycome.sremate.tools.MySQLQueryTool;
import com.yycome.sremate.tools.SkillQueryTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

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
     */
    @Bean
    public ToolCallbackProvider sreTools(
            SkillQueryTool skillQueryTool,
            MySQLQueryTool mySQLQueryTool,
            HttpQueryTool httpQueryTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(skillQueryTool, mySQLQueryTool, httpQueryTool)
                .build();
    }
}
