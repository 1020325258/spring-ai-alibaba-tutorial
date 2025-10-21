package com.alibaba.yycome.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.yycome.enums.StateKeyEnum;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class PromptTemplateUtil {

    public static Message getPlannerMessage(OverAllState state) throws IOException {
        // 读取 resources/prompts 下的 md 文件
        ClassPathResource resource = new ClassPathResource("prompts/planner.md");
        String template = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        // 替换 {{ CURRENT_TIME }} 占位符
        String systemPrompt = template.replace("{{ CURRENT_TIME }}", LocalDateTime.now().toString());
        // 替换 {{ max_step_num }} 占位符
        systemPrompt = systemPrompt.replace("{{ max_step_num }}", state.value(StateKeyEnum.MAX_STEP_NUM.getKey(), 3).toString());

        SystemMessage systemMessage = new SystemMessage(systemPrompt);
        return systemMessage;
    }

}
