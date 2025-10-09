package com.alibaba.yycome;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(exclude = {
        org.springframework.ai.mcp.client.autoconfigure.SseHttpClientTransportAutoConfiguration.class
})
public class Application {

    @Bean
    public ChatClient mcpAgent(
            ChatClient.Builder builder,
            ToolCallbackProvider tools
    ) {
        System.out.println(111);
        ChatClient mcpAgent = builder
                .defaultToolCallbacks(tools)
                .build();

        String query = "sql 的更新流程是什么？";

        String content = mcpAgent.prompt(query).call().content();

        System.out.println("content: " + content);

        return mcpAgent;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
