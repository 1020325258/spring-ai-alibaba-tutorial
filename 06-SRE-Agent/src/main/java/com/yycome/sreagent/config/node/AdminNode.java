package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import com.yycome.sreagent.infrastructure.service.TracingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 管理节点：处理环境查看/切换等系统命令，以及模糊意图的智能推荐
 *
 * 职责：
 * 1. 环境切换（纯代码逻辑）
 * 2. 模糊意图推荐：当 RouterNode 无法识别用户意图时，通过 LLM 分析用户输入和可用能力列表，输出推荐
 *
 * 环境匹配策略（按优先级）：
 * 1. 精确匹配 key（如 "offline-beta"、"nrs-escrow"）
 * 2. 别名匹配（枚举定义，支持前缀包含）
 * 3. 描述关键词匹配（兜底）
 */
public class AdminNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(AdminNode.class);

    /** 环境别名枚举 - 每个环境一个枚举值，aliases 支持多个别名 */
    private enum EnvAlias {
        OFFLINE_BETA("offline-beta", "基准", "离线", "beta", "offline"),
        NRS_ESCROW("nrs-escrow", "测试", "escrow", "nrs");

        private final String envKey;
        private final Set<String> aliases;

        EnvAlias(String envKey, String... aliases) {
            this.envKey = envKey;
            this.aliases = new HashSet<>();
            for (String alias : aliases) {
                this.aliases.add(alias.toLowerCase());
            }
        }

        public String getEnvKey() {
            return envKey;
        }

        public boolean matches(String input) {
            // 匹配 key 或任意别名
            if (input.contains(envKey)) {
                return true;
            }
            return aliases.stream().anyMatch(input::contains);
        }
    }

    private final EnvironmentConfig environmentConfig;
    private final ReactAgent adminAgent;
    private final TracingService tracingService;
    private final ChatModel chatModel;
    private final SkillRegistry skillRegistry;
    private final EntityRegistry entityRegistry;

    public AdminNode(EnvironmentConfig environmentConfig,
                     ReactAgent adminAgent,
                     TracingService tracingService,
                     ChatModel chatModel,
                     SkillRegistry skillRegistry,
                     EntityRegistry entityRegistry) {
        this.environmentConfig = environmentConfig;
        this.adminAgent = adminAgent;
        this.tracingService = tracingService;
        this.chatModel = chatModel;
        this.skillRegistry = skillRegistry;
        this.entityRegistry = entityRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String input = state.value(OverAllState.DEFAULT_INPUT_KEY, "");
        log.info("AdminNode 收到 input: {}", input);
        String result = executeAdmin(input);
        log.info("AdminNode 执行完成");
        return Map.of("result", result);
    }

    private String executeAdmin(String input) {
        String lowerInput = input.toLowerCase();
        Map<String, String> envs = environmentConfig.getAvailableEnvironments();

        // 查看/切换环境：纯代码处理（优先于 LLM）
        // "当前环境"、"查看环境"、"环境列表"、"切换到xxx"
        if (lowerInput.contains("环境") || lowerInput.contains("switch") || lowerInput.contains("env")) {
            String envResult = handleEnvSwitch(lowerInput, envs);
            if (envResult != null) return envResult;
            // 用户提到了"环境"但没有找到具体环境，返回环境列表
            return buildEnvListResponse();
        }

        // 简单规则过滤：输入太短直接返回默认提示
        if (input.trim().length() < 5) {
            return buildDefaultHelpResponse();
        }

        // 模糊意图：调用 LLM 推荐相似能力
        return recommendCapabilities(input);
    }

    private String handleEnvSwitch(String lowerInput, Map<String, String> envs) {
        // 1. 精确匹配 key
        String targetEnv = null;
        for (String envKey : envs.keySet()) {
            if (lowerInput.contains(envKey.toLowerCase())) {
                targetEnv = envKey;
                break;
            }
        }

        // 2. 别名匹配（枚举）
        if (targetEnv == null) {
            for (EnvAlias alias : EnvAlias.values()) {
                if (alias.matches(lowerInput) && envs.containsKey(alias.getEnvKey())) {
                    targetEnv = alias.getEnvKey();
                    break;
                }
            }
        }

        // 3. 描述关键词匹配（兜底）
        if (targetEnv == null) {
            outer:
            for (Map.Entry<String, String> entry : envs.entrySet()) {
                String envDesc = entry.getValue();
                for (String token : envDesc.split("[-\\s]+")) {
                    if (token.length() >= 2 && lowerInput.contains(token.toLowerCase())) {
                        targetEnv = entry.getKey();
                        break outer;
                    }
                }
            }
        }

        if (targetEnv != null) {
            boolean success = environmentConfig.switchEnv(targetEnv);
            if (success) {
                return "已切换到环境：**" + environmentConfig.getCurrentEnvDescription()
                        + "**（`" + environmentConfig.getCurrentEnv() + "`）";
            } else {
                return "切换失败：未知环境 `" + targetEnv + "`";
            }
        }

        return null; // 未识别为环境切换命令
    }

    /** 显示当前环境和可用列表（回复用户咨询环境列表时使用） */
    private String buildEnvListResponse() {
        Map<String, String> envs = environmentConfig.getAvailableEnvironments();
        StringBuilder sb = new StringBuilder();
        sb.append("**当前环境**：").append(environmentConfig.getCurrentEnvDescription())
                .append("（`").append(environmentConfig.getCurrentEnv()).append("`）\n\n");
        sb.append("**可用环境**：\n");
        envs.forEach((key, desc) -> {
            String marker = key.equals(environmentConfig.getCurrentEnv()) ? " ✓" : "";
            sb.append("- `").append(key).append("` — ").append(desc).append(marker).append("\n");
        });
        return sb.toString();
    }

    /**
     * 构建可用能力列表（Skills + 实体）
     * 用于 LLM 推荐匹配
     */
    private String buildAvailableCapabilities() {
        StringBuilder sb = new StringBuilder();

        // 1. Skills
        sb.append("【排查能力】\n");
        List<SkillMetadata> skills = skillRegistry.listAll();
        if (skills.isEmpty()) {
            sb.append("（暂无）\n");
        } else {
            for (SkillMetadata skill : skills) {
                sb.append("- ").append(skill.getName())
                        .append("：").append(skill.getDescription())
                        .append("\n");
            }
        }

        sb.append("\n【查询实体】\n");
        // 2. Entities
        List<OntologyEntity> entities = entityRegistry.getOntology().getEntities();
        for (OntologyEntity entity : entities) {
            sb.append("- ").append(entity.getName())
                    .append("（").append(entity.getDisplayName()).append("）");
            if (entity.getAliases() != null && !entity.getAliases().isEmpty()) {
                sb.append("：别名[").append(String.join(", ", entity.getAliases())).append("]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 调用 LLM 推荐相似能力
     */
    private String recommendCapabilities(String userInput) {
        String capabilities = buildAvailableCapabilities();

        String prompt = """
                你是一个能力推荐助手。用户的问题意图不明确，请分析用户输入，推荐最相关的能力。

                ## 用户输入
                %s

                ## 可用能力列表
                %s

                ## 输出规则

                **有匹配能力时：**
                - 列出所有语义相关的能力（不要遗漏），最多 3 个
                - 使用能力的 displayName 或描述，不要暴露技术名称
                - 格式：
                  "您可能想问：
                  1. {能力描述}
                  2. {能力描述}（如有多个）
                  ...
                  请告诉我您想查询哪一种？"

                **无匹配能力时：**
                - 输出：
                  "抱歉，我无法理解您的问题。请描述您的业务需求，例如：
                  - 查询订单合同
                  - 查询报价单
                  - 排查签约问题"

                只输出推荐内容，不要其他解释。
                """.formatted(userInput, capabilities);

        try {
            var response = chatModel.call(new Prompt(prompt));
            String result = response.getResult().getOutput().getText().trim();
            log.info("[AdminNode] 能力推荐完成");
            return result;
        } catch (Exception e) {
            log.error("[AdminNode] 能力推荐失败: {}", e.getMessage(), e);
            return buildDefaultHelpResponse();
        }
    }

    /**
     * 默认帮助响应
     */
    private String buildDefaultHelpResponse() {
        return """
                抱歉，我无法理解您的问题。请描述您的业务需求，例如：
                - 查询订单合同
                - 查询报价单
                - 排查签约问题

                您也可以说"查看环境"了解当前配置。
                """;
    }
}
