package com.alibaba.yycome.config;

import com.alibaba.yycome.util.ResourceUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/evaluation.md")
    private Resource evaluationPrompt;

    @Bean
    public ChatClient analysisAgent(ChatClient.Builder builder) {
        return builder
                .defaultSystem(ResourceUtil.loadResourceAsString(evaluationPrompt))
                .build();
    }

}
