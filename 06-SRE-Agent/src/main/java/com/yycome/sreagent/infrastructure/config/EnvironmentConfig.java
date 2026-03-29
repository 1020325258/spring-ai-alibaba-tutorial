package com.yycome.sreagent.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 环境配置服务
 * 管理当前运行环境和可用环境列表
 */
@Slf4j
@Service
public class EnvironmentConfig {

    /**
     * 可用环境列表
     * key: 环境标识, value: 环境描述
     */
    private static final LinkedHashMap<String, String> AVAILABLE_ENVIRONMENTS = new LinkedHashMap<>() {{
        put("nrs-escrow", "nrs-escrow 测试环境");
        put("offline-beta", "offline-beta 基准环境");
    }};

    /** 当前环境 */
    private String currentEnv = "nrs-escrow";  // 默认使用 nrs-escrow

    /**
     * 获取当前环境
     */
    public String getCurrentEnv() {
        return currentEnv;
    }

    /**
     * 切换环境
     * @param env 环境标识
     * @return 切换是否成功
     */
    public boolean switchEnv(String env) {
        if (!AVAILABLE_ENVIRONMENTS.containsKey(env)) {
            log.warn("[ENV] 未知环境: {}, 可用环境: {}", env, AVAILABLE_ENVIRONMENTS.keySet());
            return false;
        }
        String oldEnv = this.currentEnv;
        this.currentEnv = env;
        log.info("[ENV] 环境切换: {} -> {}", oldEnv, env);
        return true;
    }

    /**
     * 获取所有可用环境
     */
    public Map<String, String> getAvailableEnvironments() {
        return Collections.unmodifiableMap(AVAILABLE_ENVIRONMENTS);
    }

    /**
     * 获取当前环境描述
     */
    public String getCurrentEnvDescription() {
        return AVAILABLE_ENVIRONMENTS.getOrDefault(currentEnv, currentEnv);
    }

    /**
     * 判断是否为有效环境
     */
    public boolean isValidEnv(String env) {
        return AVAILABLE_ENVIRONMENTS.containsKey(env);
    }

    /**
     * 添加新环境（运行时动态添加）
     */
    public void addEnvironment(String envKey, String envDescription) {
        AVAILABLE_ENVIRONMENTS.put(envKey, envDescription);
        log.info("[ENV] 添加新环境: {} - {}", envKey, envDescription);
    }
}
