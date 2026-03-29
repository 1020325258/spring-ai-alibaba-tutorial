package com.yycome.sreagent.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yycome.sreagent.infrastructure.gateway.EndpointTemplateService;
import com.yycome.sreagent.infrastructure.gateway.model.EndpointTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP 接口客户端（基础设施层）
 * 用于调用预定义的 HTTP 接口，供 Gateway 和其他基础设施组件使用
 */
@Slf4j
@Component
public class HttpEndpointClient {

    private final WebClient webClient;
    private final EndpointTemplateService endpointTemplateService;
    private final ObjectMapper objectMapper;

    public HttpEndpointClient(WebClient.Builder webClientBuilder,
                              EndpointTemplateService endpointTemplateService,
                              ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.endpointTemplateService = endpointTemplateService;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用预定义接口并返回原始 HTTP 响应体
     * 不做 responseFields 过滤，不加 wrapper 文字，直接返回接口返回的 JSON 字符串
     *
     * @param endpointId 接口标识
     * @param params     参数 Map
     * @return 接口返回的 JSON 字符串，若接口不存在或调用失败返回 null
     */
    public String callPredefinedEndpointRaw(String endpointId, Map<String, String> params) {
        try {
            EndpointTemplate template = endpointTemplateService.getTemplate(endpointId);
            if (template == null) {
                log.warn("[HttpEndpointClient] 未找到接口模板: {}", endpointId);
                return null;
            }

            Map<String, String> filledParams = endpointTemplateService.fillDefaultValues(
                    template, params != null ? params : new HashMap<>());
            String url = endpointTemplateService.buildUrl(template, filledParams);

            ResponseEntity<String> responseEntity = executeHttpRequest(template, url, filledParams)
                    .timeout(Duration.ofSeconds(template.getTimeout()))
                    .block();
            return responseEntity != null ? responseEntity.getBody() : null;
        } catch (Exception e) {
            log.error("[HttpEndpointClient] callPredefinedEndpointRaw error endpointId={}", endpointId, e);
            return null;
        }
    }

    /**
     * 调用预定义接口并返回过滤后的数据
     * 根据模板配置的 responseFields 过滤响应字段
     *
     * @param endpointId 接口标识
     * @param params     参数 Map
     * @return 过滤后的 JSON 字符串
     */
    public String callPredefinedEndpointFiltered(String endpointId, Map<String, String> params) {
        try {
            EndpointTemplate template = endpointTemplateService.getTemplate(endpointId);
            if (template == null) {
                log.warn("[HttpEndpointClient] 未找到接口模板: {}", endpointId);
                return null;
            }

            Map<String, String> safeParams = params != null ? params : new HashMap<>();
            endpointTemplateService.validateParameters(template, safeParams);

            Map<String, String> filledParams = endpointTemplateService.fillDefaultValues(template, safeParams);
            String url = endpointTemplateService.buildUrl(template, filledParams);

            ResponseEntity<String> responseEntity = executeHttpRequest(template, url, filledParams)
                    .timeout(Duration.ofSeconds(template.getTimeout()))
                    .block();

            String response = responseEntity.getBody();

            // 字段过滤：配置了 responseFields 时，直接返回过滤后的纯 JSON
            if (template.getResponseFields() != null && !template.getResponseFields().isEmpty()) {
                return filterResponseFields(response, template.getResponseFields());
            }

            return response;
        } catch (Exception e) {
            log.error("[HttpEndpointClient] callPredefinedEndpointFiltered error endpointId={}", endpointId, e);
            return null;
        }
    }

    /**
     * 列出所有可用的预定义接口
     *
     * @param category 分类名称（可选）
     * @return 可用接口列表描述
     */
    public String listAvailableEndpoints(String category) {
        return endpointTemplateService.getTemplatesDescription(category);
    }

    /**
     * 构建 HTTP 请求 Mono（GET / POST）
     */
    private Mono<ResponseEntity<String>> executeHttpRequest(EndpointTemplate template, String url, Map<String, String> filledParams) {
        String method = template.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return webClient.get()
                    .uri(url)
                    .headers(h -> { if (template.getHeaders() != null) template.getHeaders().forEach(h::add); })
                    .retrieve()
                    .toEntity(String.class);
        } else if ("POST".equalsIgnoreCase(method)) {
            String requestBody = endpointTemplateService.buildRequestBody(template, filledParams);
            if (requestBody == null) requestBody = "{}";
            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (template.getHeaders() != null) template.getHeaders().forEach(h::add); })
                    .bodyValue(requestBody)
                    .retrieve()
                    .toEntity(String.class);
        }
        return Mono.error(new IllegalArgumentException("不支持的HTTP方法: " + method));
    }

    /**
     * 从响应 JSON 中过滤出指定数组字段的指定列
     */
    private String filterResponseFields(String responseJson, Map<String, List<String>> responseFields) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode data = root.path("data");
            if (data.isMissingNode()) {
                return responseJson;
            }

            ObjectNode result = objectMapper.createObjectNode();
            for (Map.Entry<String, List<String>> entry : responseFields.entrySet()) {
                String arrayKey = entry.getKey();
                List<String> keepFields = entry.getValue();
                JsonNode arrayNode = data.path(arrayKey);
                if (!arrayNode.isArray()) continue;

                ArrayNode filtered = objectMapper.createArrayNode();
                for (JsonNode item : arrayNode) {
                    ObjectNode filteredItem = objectMapper.createObjectNode();
                    for (String field : keepFields) {
                        if (item.has(field)) {
                            filteredItem.set(field, item.get(field));
                        }
                    }
                    filtered.add(filteredItem);
                }
                result.set(arrayKey, filtered);
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[HttpEndpointClient] filterResponseFields error: {}", e.getMessage());
            return responseJson;
        }
    }
}
