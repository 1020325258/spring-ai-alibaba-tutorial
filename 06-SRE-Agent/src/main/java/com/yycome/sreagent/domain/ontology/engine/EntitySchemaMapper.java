package com.yycome.sreagent.domain.ontology.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.model.OntologyAttribute;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 实体 Schema 映射器
 * 根据 YAML 中配置的 source 规则，将原始 JSON 数据映射为标准实体结构
 */
@Slf4j
@Component
public class EntitySchemaMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将原始 JSON 数据根据实体配置映射为标准结构
     *
     * @param entity    实体定义
     * @param rawJson   原始 JSON 字符串
     * @param queryParams 查询参数
     * @return 映射后的 List<Map<String, Object>>
     */
    public List<Map<String, Object>> map(OntologyEntity entity, String rawJson, Map<String, Object> queryParams) {
        if (entity.getAttributes() == null || entity.getAttributes().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonPathResolver resolver = new JsonPathResolver(root, queryParams);

            // 检查是否有 source 配置
            boolean hasSourceConfig = entity.getAttributes().stream()
                    .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

            if (!hasSourceConfig) {
                // 没有 source 配置，使用默认解析（返回原始数据）
                return parseDefault(root);
            }

            // 使用 YAML 配置的 source 进行映射
            return mapWithSource(entity, resolver);

        } catch (Exception e) {
            log.warn("[EntitySchemaMapper] 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 使用 source 配置进行映射
     */
    private List<Map<String, Object>> mapWithSource(OntologyEntity entity, JsonPathResolver resolver) {
        List<OntologyAttribute> attributes = entity.getAttributes();

        // 分离：需要数组展平的字段和其他字段
        List<OntologyAttribute> flattenAttributes = attributes.stream()
                .filter(attr -> attr.getSource() != null && attr.getSource().endsWith("[]"))
                .collect(Collectors.toList());

        List<OntologyAttribute> normalAttributes = attributes.stream()
                .filter(attr -> attr.getSource() != null && !attr.getSource().endsWith("[]"))
                .collect(Collectors.toList());

        List<OntologyAttribute> paramAttributes = attributes.stream()
                .filter(attr -> attr.getSource() != null && attr.getSource().startsWith("$param."))
                .collect(Collectors.toList());

        // 判断是否需要展平：有展平字段配置 OR entity 配置了 flattenPath
        boolean hasFlattenConfig = !flattenAttributes.isEmpty() ||
                (entity.getFlattenPath() != null && !entity.getFlattenPath().isEmpty());

        // 如果有展平配置，使用 flattenWithInheritance
        if (hasFlattenConfig) {
            return mapWithFlatten(entity, resolver, flattenAttributes, normalAttributes, paramAttributes);
        }

        // 普通映射
        return mapNormal(entity, resolver, normalAttributes, paramAttributes);
    }

    /**
     * 带数组展平的映射
     */
    private List<Map<String, Object>> mapWithFlatten(OntologyEntity entity, JsonPathResolver resolver,
                                                       List<OntologyAttribute> flattenAttrs,
                                                       List<OntologyAttribute> normalAttrs,
                                                       List<OntologyAttribute> paramAttrs) {
        // 使用 flattenPath 配置进行展平
        String flattenPath = entity.getFlattenPath();
        if (flattenPath == null || flattenPath.isEmpty()) {
            log.warn("[EntitySchemaMapper] flattenPath 未配置，使用普通映射");
            return mapNormal(entity, resolver, normalAttrs, paramAttrs);
        }

        List<Map<String, Object>> result;

        // 判断是否是多路径模式（逗号分隔）
        if (flattenPath.contains(",")) {
            // 多路径模式：使用 merge 合并多个数组
            String[] paths = flattenPath.split(",");
            result = new ArrayList<>();
            for (String path : paths) {
                path = path.trim();
                if (!path.isEmpty()) {
                    List<Map<String, Object>> partial = resolver.flattenWithInheritance(path);
                    result.addAll(partial);
                }
            }
        } else {
            // 单路径模式：使用 flattenWithInheritance 进行数组展平
            result = resolver.flattenWithInheritance(flattenPath);
        }

        log.debug("[EntitySchemaMapper] flatten result size: {}", result.size());

        // 添加查询参数字段（如 $param.projectOrderId）
        if (!paramAttrs.isEmpty()) {
            Map<String, Object> paramsMap = new HashMap<>();
            for (OntologyAttribute attr : paramAttrs) {
                Object value = resolver.get(attr.getSource());
                paramsMap.put(attr.getName(), value);
            }
            // 将查询参数字段添加到每条记录
            for (Map<String, Object> record : result) {
                record.putAll(paramsMap);
            }
        }

        return result;
    }

    /**
     * 普通映射
     */
    private List<Map<String, Object>> mapNormal(OntologyEntity entity, JsonPathResolver resolver,
                                                 List<OntologyAttribute> normalAttrs,
                                                 List<OntologyAttribute> paramAttrs) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 对于没有展平的简单情况，返回单条记录
        Map<String, Object> record = new LinkedHashMap<>();

        // 普通字段
        for (OntologyAttribute attr : normalAttrs) {
            Object value = resolver.get(attr.getSource());
            record.put(attr.getName(), value);
        }

        // 查询参数字段
        for (OntologyAttribute attr : paramAttrs) {
            Object value = resolver.get(attr.getSource());
            record.put(attr.getName(), value);
        }

        result.add(record);
        return result;
    }

    /**
     * 默认解析（无 source 配置）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDefault(JsonNode root) {
        List<Map<String, Object>> result = new ArrayList<>();

        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                if (item.isObject()) {
                    result.add(objectMapper.convertValue(item, Map.class));
                }
            }
        }

        return result;
    }
}