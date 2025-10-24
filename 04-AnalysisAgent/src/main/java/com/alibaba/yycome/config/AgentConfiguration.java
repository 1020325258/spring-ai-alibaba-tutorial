package com.alibaba.yycome.config;

import com.alibaba.yycome.util.ResourceUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class AgentConfiguration {

    @Bean
    public ChatClient plannerAgent(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient planAcceptAgent(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ChatClient searchAgent(ChatClient.Builder builder, ToolCallbackProvider tools) {
        return builder
//                .defaultToolCallbacks(tools)
                .build();
    }

}
