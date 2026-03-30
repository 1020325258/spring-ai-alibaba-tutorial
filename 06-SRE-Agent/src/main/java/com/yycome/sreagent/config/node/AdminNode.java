package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.yycome.sreagent.infrastructure.config.EnvironmentConfig;
import com.yycome.sreagent.infrastructure.service.TracingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 管理节点：处理环境查看/切换等系统命令（无 LLM 调用，纯代码逻辑）
 *
 * 注意：admin Agent 的 LLM 问答功能已在 AgentConfiguration 中通过 adminAgent ReactAgent 实现。
 * AdminNode 仅处理纯代码逻辑（环境切换），不调用 LLM。
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

    public AdminNode(EnvironmentConfig environmentConfig,
                     ReactAgent adminAgent,
                     TracingService tracingService) {
        this.environmentConfig = environmentConfig;
        this.adminAgent = adminAgent;
        this.tracingService = tracingService;
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

        // 非环境命令：交给 LLM Agent 处理（本体模型查询、配置询问等）
        return executeLLMAgent(input);
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

    private String executeLLMAgent(String input) {
        StringBuilder resultBuilder = new StringBuilder();
        Map<String, Object> params = Map.of("input", input);
        var context = tracingService.startToolCall("adminAgent", params);

        try {
            Flux<Message> messageFlux = adminAgent.streamMessages(input);
            messageFlux
                    .filter(msg -> msg instanceof AssistantMessage am && !am.getText().isEmpty())
                    .doOnNext(msg -> resultBuilder.append(((AssistantMessage) msg).getText()))
                    .blockLast();

            String result = resultBuilder.toString();
            tracingService.endToolCall(context, result.isEmpty() ? "(无返回内容)" : result);
            return result.isEmpty() ? "（无返回内容）" : result;
        } catch (Exception e) {
            log.error("AdminNode LLM 调用失败: {}", e.getMessage(), e);
            tracingService.failToolCall(context, e);
            return "处理请求时发生错误：" + e.getMessage();
        }
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
}
