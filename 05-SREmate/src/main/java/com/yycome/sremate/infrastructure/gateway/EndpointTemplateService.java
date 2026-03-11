package com.yycome.sremate.infrastructure.gateway;

import com.yycome.sremate.infrastructure.config.EnvironmentConfig;
import com.yycome.sremate.infrastructure.gateway.model.EndpointParameter;
import com.yycome.sremate.infrastructure.gateway.model.EndpointTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 接口模板服务（基础设施层）
 * 负责加载和管理预定义接口模板
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointTemplateService {

    private final EnvironmentConfig environmentConfig;

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private static final String ENDPOINTS_PATH = "classpath:endpoints/**/*.yml";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final Map<String, EndpointTemplate> templateMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadTemplates();
    }

    public void loadTemplates() {
        try {
            Resource[] resources = resourceResolver.getResources(ENDPOINTS_PATH);
            Yaml yaml = new Yaml();

            for (Resource resource : resources) {
                try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    Map<String, Object> data = yaml.load(reader);
                    List<Map<String, Object>> endpoints = (List<Map<String, Object>>) data.get("endpoints");

                    if (endpoints != null) {
                        for (Map<String, Object> endpointData : endpoints) {
                            EndpointTemplate template = parseTemplate(endpointData);
                            templateMap.put(template.getId(), template);
                            log.info("[ENDPOINT] 加载接口模板: id={}, name={}", template.getId(), template.getName());
                        }
                    }
                }
            }

            log.info("[ENDPOINT] 成功加载 {} 个接口模板", templateMap.size());

        } catch (IOException e) {
            log.error("[ENDPOINT] 加载接口模板失败", e);
        }
    }

    public EndpointTemplate getTemplate(String id) {
        if (id == null) return null;
        return templateMap.get(id);
    }

    public List<EndpointTemplate> getTemplatesByCategory(String category) {
        return templateMap.values().stream()
                .filter(template -> category.equals(template.getCategory()))
                .collect(Collectors.toList());
    }

    public List<EndpointTemplate> getAllTemplates() {
        return new ArrayList<>(templateMap.values());
    }

    public String getTemplatesDescription() {
        return getTemplatesDescription(null);
    }

    public String getTemplatesDescription(String category) {
        List<EndpointTemplate> templates = category != null
                ? getTemplatesByCategory(category)
                : getAllTemplates();

        if (templates.isEmpty()) {
            String msg = category != null
                    ? String.format("分类【%s】下暂无可用接口。可用分类：system、database、monitoring、contract", category)
                    : "暂无可用接口，请检查配置文件";
            return msg;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("可用的接口列表：\n\n");

        for (int i = 0; i < templates.size(); i++) {
            EndpointTemplate template = templates.get(i);
            sb.append(String.format("%d. %s - %s\n", i + 1, template.getId(), template.getName()));
            sb.append(String.format("   描述: %s\n", template.getDescription()));
            sb.append("   参数: ");

            List<String> paramList = new ArrayList<>();
            if (template.getParameters() != null) {
                for (EndpointParameter param : template.getParameters()) {
                    paramList.add(String.format("%s (%s%s)",
                            param.getName(),
                            param.isRequired() ? "必需" : "可选",
                            param.getDefaultValue() != null ? ", 默认: " + param.getDefaultValue() : ""));
                }
            }
            sb.append(String.join(", ", paramList));
            sb.append("\n");

            if (template.getExamples() != null && !template.getExamples().isEmpty()) {
                sb.append(String.format("   示例: %s\n", template.getExamples().get(0)));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 构建 POST 请求体，将模板中的 ${paramName} 替换为实际参数值
     */
    public String buildRequestBody(EndpointTemplate template, Map<String, String> params) {
        if (template.getRequestBodyTemplate() == null) {
            return null;
        }
        String body = template.getRequestBodyTemplate();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String value = params.getOrDefault(paramName, "");
            matcher.appendReplacement(sb, value.replace("\\", "\\\\").replace("$", "\\$"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String buildUrl(EndpointTemplate template, Map<String, String> params) {
        String url = template.getUrlTemplate();

        // 首先替换环境占位符 ${env}
        url = url.replace("${env}", environmentConfig.getCurrentEnv());

        // 然后替换参数占位符
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(url);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            String value = params.get(paramName);

            if (value == null) {
                EndpointParameter param = findParameter(template, paramName);
                if (param != null && param.getDefaultValue() != null) {
                    value = param.getDefaultValue();
                } else {
                    value = "";
                    log.warn("[ENDPOINT] 参数 {} 未提供值且无默认值", paramName);
                }
            }

            matcher.appendReplacement(sb, value);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public void validateParameters(EndpointTemplate template, Map<String, String> params) {
        if (template.getParameters() == null) return;

        List<String> missingParams = new ArrayList<>();

        for (EndpointParameter param : template.getParameters()) {
            if (param.isRequired()) {
                String value = params.get(param.getName());
                if (value == null && param.getDefaultValue() == null) {
                    missingParams.add(param.getName());
                }
            }
        }

        if (!missingParams.isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数: " + String.join(", ", missingParams));
        }
    }

    public Map<String, String> fillDefaultValues(EndpointTemplate template, Map<String, String> params) {
        Map<String, String> result = new HashMap<>(params);

        if (template.getParameters() != null) {
            for (EndpointParameter param : template.getParameters()) {
                if (!result.containsKey(param.getName()) && param.getDefaultValue() != null) {
                    result.put(param.getName(), param.getDefaultValue());
                }
            }
        }

        return result;
    }

    private EndpointTemplate parseTemplate(Map<String, Object> data) {
        EndpointTemplate template = new EndpointTemplate();
        template.setId((String) data.get("id"));
        template.setName((String) data.get("name"));
        template.setDescription((String) data.get("description"));
        template.setCategory((String) data.get("category"));
        template.setUrlTemplate((String) data.get("urlTemplate"));
        template.setMethod((String) data.get("method"));
        template.setTimeout(data.get("timeout") != null ? ((Number) data.get("timeout")).intValue() : 30);

        List<Map<String, Object>> paramsData = (List<Map<String, Object>>) data.get("parameters");
        if (paramsData != null) {
            List<EndpointParameter> parameters = new ArrayList<>();
            for (Map<String, Object> paramData : paramsData) {
                EndpointParameter param = new EndpointParameter();
                param.setName((String) paramData.get("name"));
                param.setType((String) paramData.get("type"));
                param.setDescription((String) paramData.get("description"));
                param.setRequired(paramData.get("required") != null && (Boolean) paramData.get("required"));
                param.setDefaultValue((String) paramData.get("defaultValue"));
                param.setExample((String) paramData.get("example"));
                parameters.add(param);
            }
            template.setParameters(parameters);
        }

        template.setRequestBodyTemplate((String) data.get("requestBodyTemplate"));

        Map<String, Object> responseFieldsRaw = (Map<String, Object>) data.get("responseFields");
        if (responseFieldsRaw != null) {
            Map<String, List<String>> responseFields = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : responseFieldsRaw.entrySet()) {
                responseFields.put(entry.getKey(), (List<String>) entry.getValue());
            }
            template.setResponseFields(responseFields);
        }

        Map<String, String> headers = (Map<String, String>) data.get("headers");
        template.setHeaders(headers);

        List<String> examples = (List<String>) data.get("examples");
        template.setExamples(examples);

        return template;
    }

    private EndpointParameter findParameter(EndpointTemplate template, String paramName) {
        if (template.getParameters() == null) return null;
        return template.getParameters().stream()
                .filter(p -> paramName.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }
}
