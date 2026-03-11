package com.yycome.sremate.trigger.agent;

import com.yycome.sremate.infrastructure.gateway.EndpointTemplateService;
import com.yycome.sremate.infrastructure.gateway.model.EndpointTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP接口查询工具（触发层）
 * 用于调用HTTP接口获取系统状态或诊断信息
 * 支持两种调用方式：
 * 1. callHttpEndpoint: 传统的完整URL调用方式
 * 2. callPredefinedEndpoint: 基于预定义模板的调用方式（推荐）
 */
@Slf4j
@Component
public class HttpEndpointTool {

    private final WebClient webClient;
    private final EndpointTemplateService endpointTemplateService;

    public HttpEndpointTool(WebClient.Builder webClientBuilder, EndpointTemplateService endpointTemplateService) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.endpointTemplateService = endpointTemplateService;
    }

    /**
     * 调用预定义的系统接口（推荐方式）
     *
     * @param endpointId 预定义接口的标识，如 health-check, metrics, thread-dump
     * @param params     从用户输入中提取的参数值，如 {"host": "localhost", "port": "8080"}
     * @return 接口响应
     */
    @Tool(description = """
            【预定义接口调用】调用系统预配置的HTTP接口。

            触发条件：需要查询系统状态、监控指标或业务数据

            常用接口：
            - sign-order-list：查询子单列表，参数projectOrderId
            - contract-form-data：查询版式，参数instanceId
            - health-check：健康检查
            - metrics：性能指标

            参数：
            - endpointId：接口标识
            - params：参数Map

            示例：{"endpointId":"health-check","params":{}}""")
    public String callPredefinedEndpoint(String endpointId, Map<String, String> params) {
        log.info("[TOOL_CALL] callPredefinedEndpoint - endpointId: {}, params: {}", endpointId, params);

        try {
            EndpointTemplate template = endpointTemplateService.getTemplate(endpointId);
            if (template == null) {
                return "错误：未找到接口模板: " + endpointId + "\n使用 listAvailableEndpoints 查看可用接口";
            }

            Map<String, String> safeParams = params != null ? params : new HashMap<>();

            try {
                endpointTemplateService.validateParameters(template, safeParams);
            } catch (IllegalArgumentException e) {
                return "参数验证失败: " + e.getMessage();
            }

            Map<String, String> filledParams = endpointTemplateService.fillDefaultValues(template, safeParams);
            String url = endpointTemplateService.buildUrl(template, filledParams);
            log.info("[TOOL_CALL] 构建URL: {}", url);

            Mono<String> responseMono;
            String method = template.getMethod();

            if ("GET".equalsIgnoreCase(method)) {
                responseMono = webClient.get()
                        .uri(url)
                        .headers(headers -> {
                            if (template.getHeaders() != null) {
                                template.getHeaders().forEach(headers::add);
                            }
                        })
                        .retrieve()
                        .bodyToMono(String.class);
            } else if ("POST".equalsIgnoreCase(method)) {
                responseMono = webClient.post()
                        .uri(url)
                        .headers(headers -> {
                            if (template.getHeaders() != null) {
                                template.getHeaders().forEach(headers::add);
                            }
                        })
                        .retrieve()
                        .bodyToMono(String.class);
            } else {
                return "错误：不支持的HTTP方法: " + method;
            }

            String response = responseMono
                    .timeout(Duration.ofSeconds(template.getTimeout()))
                    .block();

            return String.format("接口: %s (%s)\n名称: %s\n响应:\n%s",
                    endpointId, template.getName(), url, response);

        } catch (Exception e) {
            log.error("[TOOL_CALL] 预定义接口调用失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }

    /**
     * 列出所有可用的预定义接口
     *
     * @param category 分类名称（可选），如 system、database、monitoring
     * @return 可用接口列表
     */
    @Tool(description = """
            【列出可用接口】查看所有预定义接口。

            触发条件：用户问"有哪些接口"、"可用接口"

            参数：
            - category：分类（可选）system/database/monitoring/contract

            示例：
            - 不填category → 列出全部
            - category=contract → 只列出签约相关接口""")
    public String listAvailableEndpoints(String category) {
        log.info("[TOOL_CALL] listAvailableEndpoints - category: {}", category);
        return endpointTemplateService.getTemplatesDescription(category);
    }

    /**
     * 调用HTTP接口（传统方式，向后兼容）
     *
     * @param url    接口URL
     * @param method HTTP方法（GET/POST）
     * @param params 请求参数（POST请求的body）
     * @return 接口响应
     */
    @Tool(description = """
            调用HTTP接口获取系统状态或诊断信息（传统方式）。
            url参数是完整的接口地址，method参数是HTTP方法（GET或POST），
            params参数是请求参数（仅POST请求需要，JSON格式）。
            推荐使用callPredefinedEndpoint调用预定义接口，更简单安全。""")
    public String callHttpEndpoint(String url, String method, Map<String, Object> params) {
        log.info("[TOOL_CALL] callHttpEndpoint - URL: {}, 方法: {}", url, method);

        try {
            Mono<String> responseMono;

            if ("GET".equalsIgnoreCase(method)) {
                responseMono = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class);
            } else if ("POST".equalsIgnoreCase(method)) {
                responseMono = webClient.post()
                        .uri(url)
                        .bodyValue(params != null ? params : "{}")
                        .retrieve()
                        .bodyToMono(String.class);
            } else {
                return "错误：不支持的HTTP方法: " + method;
            }

            String response = responseMono
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return String.format("接口: %s\n方法: %s\n响应:\n%s", url, method, response);

        } catch (Exception e) {
            log.error("[TOOL_CALL] HTTP接口调用失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }
}
