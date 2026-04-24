package com.yycome.sreagent.config.infra;

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
     * 环境配置
     * key: 环境标识, value: 环境信息
     */
    private static final LinkedHashMap<String, EnvInfo> ENVIRONMENTS = new LinkedHashMap<>() {{
        put("nrs-escrow", new EnvInfo("nrs-escrow 测试环境",
                "http://utopia-nrs-sales-project.nrs-escrow.ttb.test.ke.com",
                "http://nrs-order-service.nrs-escrow.ttb.test.ke.com"));
        put("offline-beta", new EnvInfo("offline-beta 基准环境",
                "http://utopia-nrs-sales-project.offline-beta.ttb.test.ke.com",
                "http://nrs-order-service.offline-beta.ttb.test.ke.com"));
        put("prod", new EnvInfo("生产环境",
                "http://preview.i.nrs-sales-project.home.ke.com",
                "http://i.nrs-order-service.home.ke.com"));
    }};

    /** 当前环境 */
    private String currentEnv = "nrs-escrow";  // 默认使用 nrs-escrow

    /**
     * 环境信息
     */
    public record EnvInfo(String description, String salesProjectBaseUrl, String orderServiceBaseUrl) {}

    /**
     * 获取当前环境标识
     */
    public String getCurrentEnv() {
        return currentEnv;
    }

    /**
     * 获取当前环境的销售项目服务基础 URL
     */
    public String getCurrentBaseUrl() {
        EnvInfo info = ENVIRONMENTS.get(currentEnv);
        return info != null ? info.salesProjectBaseUrl() : null;
    }

    /**
     * 获取当前环境的订单服务基础 URL
     */
    public String getOrderServiceBaseUrl() {
        EnvInfo info = ENVIRONMENTS.get(currentEnv);
        return info != null ? info.orderServiceBaseUrl() : null;
    }

    /**
     * 根据服务名获取基础 URL
     * @param serviceName 服务名：sales-project, order-service
     */
    public String getBaseUrl(String serviceName) {
        return switch (serviceName) {
            case "sales-project" -> getCurrentBaseUrl();
            case "order-service" -> getOrderServiceBaseUrl();
            default -> {
                log.warn("[ENV] 未知服务: {}", serviceName);
                yield null;
            }
        };
    }

    /**
     * 切换环境
     * @param env 环境标识
     * @return 切换是否成功
     */
    public boolean switchEnv(String env) {
        if (!ENVIRONMENTS.containsKey(env)) {
            log.warn("[ENV] 未知环境: {}, 可用环境: {}", env, ENVIRONMENTS.keySet());
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
        Map<String, String> result = new LinkedHashMap<>();
        ENVIRONMENTS.forEach((k, v) -> result.put(k, v.description()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取当前环境描述
     */
    public String getCurrentEnvDescription() {
        EnvInfo info = ENVIRONMENTS.get(currentEnv);
        return info != null ? info.description() : currentEnv;
    }

    /**
     * 判断是否为有效环境
     */
    public boolean isValidEnv(String env) {
        return ENVIRONMENTS.containsKey(env);
    }

    /**
     * 添加新环境（运行时动态添加）
     */
    public void addEnvironment(String envKey, String description, String salesProjectBaseUrl, String orderServiceBaseUrl) {
        ENVIRONMENTS.put(envKey, new EnvInfo(description, salesProjectBaseUrl, orderServiceBaseUrl));
        log.info("[ENV] 添加新环境: {} - {} (sales: {}, order: {})", envKey, description, salesProjectBaseUrl, orderServiceBaseUrl);
    }
}
