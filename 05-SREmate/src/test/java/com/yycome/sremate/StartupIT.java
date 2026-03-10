package com.yycome.sremate;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动验证测试 - 验证应用上下文能正常加载，所有 Bean 能正常注入
 * 每次修改配置类、新增 Bean、改动注解后必须运行此测试
 */
@SpringBootTest(properties = "sre.console.enabled=false")
@ActiveProfiles("local")
class StartupIT {

    @Autowired
    private ChatClient sreAgent;

    @Test
    void applicationContext_shouldLoad() {
        // 只要 Spring 上下文能正常启动，此测试即通过
        assertThat(sreAgent).isNotNull();
    }

    @Test
    void sreAgent_shouldRespondToSimpleQuestion() {
        String response = sreAgent.prompt()
                .user("你好，你是谁？")
                .call()
                .content();

        System.out.println("=== SREmate 自我介绍 ===\n" + response);
        assertThat(response).isNotBlank();
    }
}
