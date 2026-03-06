package com.yycome.sremate.service;

import com.yycome.sremate.domain.EndpointParameter;
import com.yycome.sremate.domain.EndpointTemplate;
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
 * 接口模板服务
 * 负责加载和管理预定义接口模板
 */
@Slf4j
@Service
public class EndpointTemplateService {

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private static final String ENDPOINTS_PATH = "classpath:endpoints/**/*.yml";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final Map<String, EndpointTemplate> templateMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadTemplates();
    }

    /**
     * 加载所有接口模板
     */
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

    /**
     * 根据 ID 获取模板
     *
     * @param id 接口标识
     * @return 接口模板，不存在则返回 null
     */
    public EndpointTemplate getTemplate(String id) {
        return templateMap.get(id);
    }

    /**
     * 获取分类下所有模板
     *
     * @param category 分类名称
     * @return 模板列表
     */
    public List<EndpointTemplate> getTemplatesByCategory(String category) {
        return templateMap.values().stream()
                .filter(template -> category.equals(template.getCategory()))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有模板
     *
     * @return 所有模板列表
     */
    public List<EndpointTemplate> getAllTemplates() {
        return new ArrayList<>(templateMap.values());
    }

    /**
     * 获取所有模板描述（供 LLM 选择）
     *
     * @return 模板描述字符串
     */
    public String getTemplatesDescription() {
        return getTemplatesDescription(null);
    }

    /**
     * 获取指定分类的模板描述
     *
     * @param category 分类名称，null 表示所有分类
     * @return 模板描述字符串
     */
    public String getTemplatesDescription(String category) {
        List<EndpointTemplate> templates = category != null
                ? getTemplatesByCategory(category)
                : getAllTemplates();

        if (templates.isEmpty()) {
            return "暂无可用接口";
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
     * 构建完整 URL
     *
     * @param template 接口模板
     * @param params 参数值映射
     * @return 完整 URL
     */
    public String buildUrl(EndpointTemplate template, Map<String, String> params) {
        String url = template.getUrlTemplate();

        // 替换占位符
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(url);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            String value = params.get(paramName);

            if (value == null) {
                // 查找默认值
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

    /**
     * 验证参数
     *
     * @param template 接口模板
     * @param params 参数值映射
     * @throws IllegalArgumentException 参数验证失败时抛出
     */
    public void validateParameters(EndpointTemplate template, Map<String, String> params) {
        if (template.getParameters() == null) {
            return;
        }

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

    /**
     * 填充参数默认值
     *
     * @param template 接口模板
     * @param params 参数值映射
     * @return 填充后的参数映射
     */
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

    /**
     * 解析模板数据
     */
    private EndpointTemplate parseTemplate(Map<String, Object> data) {
        EndpointTemplate template = new EndpointTemplate();
        template.setId((String) data.get("id"));
        template.setName((String) data.get("name"));
        template.setDescription((String) data.get("description"));
        template.setCategory((String) data.get("category"));
        template.setUrlTemplate((String) data.get("urlTemplate"));
        template.setMethod((String) data.get("method"));
        template.setTimeout(data.get("timeout") != null ? ((Number) data.get("timeout")).intValue() : 30);

        // 解析参数
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

        // 解析请求头
        Map<String, String> headers = (Map<String, String>) data.get("headers");
        template.setHeaders(headers);

        // 解析示例
        List<String> examples = (List<String>) data.get("examples");
        template.setExamples(examples);

        return template;
    }

    /**
     * 查找参数定义
     */
    private EndpointParameter findParameter(EndpointTemplate template, String paramName) {
        if (template.getParameters() == null) {
            return null;
        }

        return template.getParameters().stream()
                .filter(p -> paramName.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }
}
