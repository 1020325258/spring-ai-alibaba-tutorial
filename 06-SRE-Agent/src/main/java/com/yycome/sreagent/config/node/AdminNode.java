package com.yycome.sreagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.yycome.sreagent.config.infra.EnvironmentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 管理节点：处理环境查看/切换等后台管理命令
 *
 * 职责：环境切换（纯代码逻辑）
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

    public AdminNode(EnvironmentConfig environmentConfig) {
        this.environmentConfig = environmentConfig;
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

        // 查看/切换环境
        if (lowerInput.contains("环境") || lowerInput.contains("switch") || lowerInput.contains("env")) {
            String envResult = handleEnvSwitch(lowerInput, envs);
            if (envResult != null) return envResult;
        }
        return buildEnvListResponse();
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

    /** 显示当前环境和可用列表 */
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
