package com.alibaba.yycome;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@SpringBootApplication
public class Application {
    private String userInput1 = "mysql的面试题有哪些？";

    @Autowired
    private Map<String, ToolCallbackProvider> tooolCallbacks;

    @Bean
    public CommandLineRunner predefinedQuestions(ChatClient.Builder chatClientBuilder, ToolCallbackProvider tools,
                                                 ConfigurableApplicationContext context) {

        return args -> {

            var chatClient = chatClientBuilder
                    .defaultToolCallbacks(tools)
                    .build();

            System.out.println("\n>>> QUESTION: " + userInput1);
            System.out.println("\n>>> ASSISTANT: " + chatClient.prompt(userInput1).call().content());

            context.close();
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
