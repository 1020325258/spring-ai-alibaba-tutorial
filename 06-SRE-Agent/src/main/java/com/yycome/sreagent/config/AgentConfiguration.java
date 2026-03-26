package com.yycome.sreagent.config;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.yycome.sreagent.trigger.agent.OntologyQueryTool;
import com.yycome.sreagent.trigger.agent.ReadSkillTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

/**
 * SRE-Agent 配置类
 * 配置多 Agent 的 ChatClient
 */
@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/sre-agent.md")
    private Resource sreAgentPrompt;

    /**
     * Supervisor Agent - 主 Agent
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public ChatClient supervisorAgent(ChatClient.Builder builder,
                                      ToolCallbackProvider tools,
                                      SkillRegistry skillRegistry) throws Exception {
        String promptContent = sreAgentPrompt.getContentAsString(StandardCharsets.UTF_8);

        // 添加 Available Skills 列表
        String skillsList = buildSkillsList(skillRegistry);
        String fullPrompt = promptContent + "\n\n" + skillsList;

        return builder
                .defaultSystem(fullPrompt)
                .defaultToolCallbacks(tools)
                .build();
    }

    /**
     * Query Agent - 查询 Agent
     */
    @Bean
    public ChatClient queryAgent(ChatClient.Builder builder,
                                  ToolCallbackProvider tools) throws Exception {
        String systemPrompt = """
            你是一个数据查询专家。
            根据用户的问题，调用 ontologyQuery 工具获取数据。
            返回查询结果（JSON 格式）。
            """;

        return builder
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(tools)
                .build();
    }

    /**
     * Investigate Agent - 排查 Agent
     */
    @Bean
    public ChatClient investigateAgent(ChatClient.Builder builder,
                                        ToolCallbackProvider tools) throws Exception {
        String systemPrompt = """
            你是一个问题排查专家。
            1. 先调用 read_skill 加载对应的 Skill
            2. 按照 Skill 的指令逐步排查
            3. 调用 ontologyQuery 获取数据
            4. 分析数据，输出结论（包含断点位置、可能原因、建议操作）
            """;

        return builder
                .defaultSystem(systemPrompt)
                .defaultToolCallbacks(tools)
                .build();
    }

    /**
     * 注册所有工具
     */
    @Bean
    public ToolCallbackProvider sreTools(OntologyQueryTool ontologyQueryTool,
                                          ReadSkillTool readSkillTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(ontologyQueryTool, readSkillTool)
                .build();
    }

    /**
     * 构建 Skills 列表
     * 注意：SkillRegistry 扫描到的 skills 会在运行时通过 read_skill 工具获取
     */
    private String buildSkillsList(SkillRegistry skillRegistry) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Available Skills\n\n");

        // TODO: 运行时通过 skillRegistry 获取 skills 列表
        // 目前硬编码示例 skills
        sb.append("- missing-personal-quote-diagnosis: 排查合同发起时缺少个性化报价的原因\n");
        sb.append("- sales-contract-sign-dialog-diagnosis: 排查销售/正签合同弹窗提示\"请先完成报价\"的原因\n");

        return sb.toString();
    }
}
