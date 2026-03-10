package com.yycome.sremate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 启动验证测试 - 验证应用上下文能正常加载，所有 Bean 能正常注入
 * 每次修改配置类、新增 Bean、改动注解后必须运行此测试
 */
class StartupIT extends BaseSREIT {

    @Test
    void applicationContext_shouldLoad() {
        assertThat(sreAgent).isNotNull();
    }

    @Test
    void sreAgent_shouldRespondToSimpleQuestion() {
        String response = ask("你好，你是谁？");
        assertThat(response).isNotBlank();
    }
}
