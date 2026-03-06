package com.yycome.sremate.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP接口查询工具
 * 用于调用HTTP接口获取系统状态或诊断信息
 */
@Slf4j
@Component
public class HttpQueryTool {

    private final WebClient webClient;

    public HttpQueryTool(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 调用HTTP接口
     *
     * @param url 接口URL
     * @param method HTTP方法（GET/POST）
     * @param params 请求参数（POST请求的body）
     * @return 接口响应
     */
    @Tool(description = "调用HTTP接口获取系统状态或诊断信息。" +
            "url参数是完整的接口地址，method参数是HTTP方法（GET或POST），" +
            "params参数是请求参数（仅POST请求需要，JSON格式）。")
    public String callHttpEndpoint(String url, String method, Map<String, Object> params) {
        log.info("调用HttpQueryTool - URL: {}, 方法: {}", url, method);

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
            log.error("HTTP接口调用失败", e);
            return "接口调用失败: " + e.getMessage();
        }
    }
}
