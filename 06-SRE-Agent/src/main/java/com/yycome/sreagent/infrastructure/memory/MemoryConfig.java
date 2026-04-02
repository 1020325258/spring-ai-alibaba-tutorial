package com.yycome.sreagent.infrastructure.memory;

import com.yycome.sreagent.config.infra.SessionProperties;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryConfig {

    @Bean
    public MessageWindowChatMemory messageWindowChatMemory(SessionProperties props) {
        // maxRecentTurns=5 轮 → 10 条消息（每轮 user+assistant 各一条）
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(props.getMaxRecentTurns() * 2)
                .build();
    }
}
