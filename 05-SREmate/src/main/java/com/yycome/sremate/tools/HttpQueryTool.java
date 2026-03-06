package com.yycome.sremate.tools;

import com.yycome.sremate.domain.EndpointTemplate;
import com.yycome.sremate.service.EndpointTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP接口查询工具
 * 用于调用HTTP接口获取系统状态或诊断信息
 * 支持两种调用方式：
 * 1. callHttpEndpoint: 传统的完整URL调用方式
 * 2. callPredefinedEndpoint: 基于预定义模板的调用方式（推荐）
 */
@Slf4j
@Component
public class HttpQueryTool {

    private final WebClient webClient;
    private final EndpointTemplateService endpointTemplateService;

    public HttpQueryTool(WebClient.Builder webClientBuilder, EndpointTemplateService endpointTemplateService) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.endpointTemplateService = endpointTemplateService;
    }

    /**
     * 调用预定义的系统接口（推荐方式）
     * LLM只需提供接口ID和参数值，无需知道完整URL
     *
     * @param endpointId 预定义接口的标识，如 health-check, metrics, thread-dump
     * @param params 从用户输入中提取的参数值，如 {"host": "localhost", "port": "8080"}
     * @return 接口响应
     */
    @Tool(description = "调用预定义的接口，用于获取系统状态、诊断信息或业务数据。" +
            "endpointId参数是预定义接口的标识，常用接口包括：" +
            "- sign-order-list: 查询项目订单的子单/S单列表（签约业务相关）" +
            "- health-check: 应用健康检查" +
            "- metrics: 应用性能指标" +
            "params参数是从用户输入中提取的参数值。" +
            "当用户询问\"查询某订单的子单\"、\"子单列表\"、\"S单\"等签约相关问题时，使用sign-order-list接口，参数projectOrderId为订单号。")
    public String callPredefinedEndpoint(String endpointId, Map<String, String> params) {
        log.info("[TOOL_CALL] callPredefinedEndpoint - endpointId: {}, params: {}", endpointId, params);

        try {
            // 1. 获取模板
            EndpointTemplate template = endpointTemplateService.getTemplate(endpointId);
            if (template == null) {
                return "错误：未找到接口模板: " + endpointId + "\n使用 listAvailableEndpoints 查看可用接口";
            }

            // 2. 准备参数（处理null情况）
            Map<String, String> safeParams = params != null ? params : new HashMap<>();

            // 3. 验证参数
            try {
                endpointTemplateService.validateParameters(template, safeParams);
            } catch (IllegalArgumentException e) {
                return "参数验证失败: " + e.getMessage();
            }

            // 4. 填充默认值
            Map<String, String> filledParams = endpointTemplateService.fillDefaultValues(template, safeParams);

            // 5. 构建URL
            String url = endpointTemplateService.buildUrl(template, filledParams);
            log.info("[TOOL_CALL] 构建URL: {}", url);

            // 6. 调用接口
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
    @Tool(description = "列出所有可用的预定义接口，帮助选择合适的接口进行调用。" +
            "category参数是可选的分类名称（system、database、monitoring、contract），不提供则列出所有接口。")
    public String listAvailableEndpoints(String category) {
        log.info("[TOOL_CALL] listAvailableEndpoints - category: {}", category);
        return endpointTemplateService.getTemplatesDescription(category);
    }

    /**
     * 调用HTTP接口（传统方式，向后兼容）
     *
     * @param url 接口URL
     * @param method HTTP方法（GET/POST）
     * @param params 请求参数（POST请求的body）
     * @return 接口响应
     */
    @Tool(description = "调用HTTP接口获取系统状态或诊断信息（传统方式）。" +
            "url参数是完整的接口地址，method参数是HTTP方法（GET或POST），" +
            "params参数是请求参数（仅POST请求需要，JSON格式）。" +
            "推荐使用callPredefinedEndpoint调用预定义接口，更简单安全。")
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
