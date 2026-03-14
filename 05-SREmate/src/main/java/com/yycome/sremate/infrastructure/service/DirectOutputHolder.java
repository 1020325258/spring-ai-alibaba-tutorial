package com.yycome.sremate.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 直接输出持有者
 * 用于存储数据查询类工具的结果，绕过 LLM 处理直接输出。
 * 支持按本体论关联关系智能合并多工具结果。
 */
@Slf4j
@Component
public class DirectOutputHolder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 存储所有工具结果 */
    private final List<ToolResult> results = new ArrayList<>();

    /** 记录调用过的工具类型（即使返回空数据也能追踪） */
    private final Set<String> invokedTypes = new LinkedHashSet<>();

    /**
     * 累积添加输出内容（追加模式）
     */
    public synchronized void append(String output) {
        if (output == null || output.isBlank()) return;
        String type = detectType(output);
        results.add(new ToolResult(type, output));
        invokedTypes.add(type);
        log.debug("[DirectOutput] 追加结果: type={}, length={}", type, output.length());
    }

    /**
     * 标记某个类型被调用过（用于空结果场景）
     */
    public synchronized void markTypeInvoked(String type) {
        invokedTypes.add(type);
    }

    /**
     * 仅当当前无内容时才写入
     * @deprecated 使用 append() 替代
     */
    @Deprecated
    public synchronized boolean setIfAbsent(String output) {
        if (results.isEmpty()) {
            append(output);
            return true;
        }
        return false;
    }

    /**
     * 获取并清除直接输出内容
     * 多结果时按本体论关系智能合并
     */
    public synchronized String getAndClear() {
        if (results.isEmpty()) {
            return null;
        }
        // 即使只有一个结果，如果有额外的类型标记（markTypeInvoked），也需要智能合并
        if (results.size() == 1 && invokedTypes.size() <= 1) {
            String json = results.get(0).json();
            results.clear();
            invokedTypes.clear();
            return json;
        }

        String merged = trySmartMerge();
        results.clear();
        invokedTypes.clear();
        return merged;
    }

    public synchronized boolean hasOutput() {
        return !results.isEmpty();
    }

    public synchronized void clear() {
        results.clear();
        invokedTypes.clear();
    }

    public synchronized int size() {
        return results.size();
    }

    /**
     * 智能合并：按本体论关系嵌套组织数据
     */
    private String trySmartMerge() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        List<Map<String, Object>> relations = new ArrayList<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();

        for (ToolResult result : results) {
            List<Map<String, Object>> items = parseJsonArray(result.json());
            switch (result.type()) {
                case "contract" -> contracts.addAll(items);
                case "contract_quotation_relation" -> relations.addAll(items);
                case "contract_node" -> nodes.addAll(items);
                case "contract_field" -> fields.addAll(items);
                default -> others.addAll(items);
            }
        }

        if (contracts.isEmpty()) {
            log.debug("[DirectOutput] 无 contract 主表，使用平铺合并");
            return mergeAsFlatArray();
        }

        Map<String, Map<String, Object>> contractMap = new LinkedHashMap<>();
        for (Map<String, Object> contract : contracts) {
            String code = getStringValue(contract, "contract_code", "contractCode");
            if (code != null) {
                Map<String, Object> enriched = new LinkedHashMap<>(contract);
                contractMap.put(code, enriched);
            }
        }

        // 使用类成员变量 invokedTypes
        if (invokedTypes.contains("contract_quotation_relation")) {
            for (Map<String, Object> contract : contractMap.values()) {
                contract.put("contract_quotation_relation", new ArrayList<>());
            }
            for (Map<String, Object> rel : relations) {
                String code = getStringValue(rel, "contract_code", "contractCode");
                if (code != null && contractMap.containsKey(code)) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) contractMap.get(code).get("contract_quotation_relation");
                    list.add(rel);
                }
            }
        }

        if (invokedTypes.contains("contract_node")) {
            for (Map<String, Object> contract : contractMap.values()) {
                contract.put("contract_node", new ArrayList<>());
            }
            for (Map<String, Object> node : nodes) {
                String code = getStringValue(node, "contract_code", "contractCode");
                if (code != null && contractMap.containsKey(code)) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) contractMap.get(code).get("contract_node");
                    list.add(node);
                }
            }
        }

        if (invokedTypes.contains("contract_field")) {
            for (Map<String, Object> field : fields) {
                String code = getStringValue(field, "contract_code", "contractCode");
                if (code != null && contractMap.containsKey(code)) {
                    contractMap.get(code).put("contract_field", field);
                }
            }
        }

        List<Object> finalResult = new ArrayList<>(contractMap.values());
        finalResult.addAll(others);

        try {
            return objectMapper.writeValueAsString(finalResult);
        } catch (Exception e) {
            log.error("[DirectOutput] JSON 序列化失败", e);
            return mergeAsFlatArray();
        }
    }

    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonArray(String json) {
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return objectMapper.readValue(trimmed, List.class);
            }
            if (trimmed.startsWith("{")) {
                Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
                return Collections.singletonList(map);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("[DirectOutput] JSON 解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String detectType(String json) {
        String lower = json.toLowerCase();
        if (lower.contains("\"bill_code\"") || lower.contains("\"billcode\"")) {
            return "contract_quotation_relation";
        }
        if (lower.contains("\"node_type\"") || lower.contains("\"fire_time\"")) {
            return "contract_node";
        }
        if (lower.contains("\"_shardtable\"")) {
            return "contract_field";
        }
        if (lower.contains("\"platform_instance_id\"")) {
            return "contract";
        }
        return "contract";
    }

    private String mergeAsFlatArray() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ToolResult result : results) {
            String json = result.json().trim();
            if (!first) sb.append(",");
            first = false;
            if (json.startsWith("[") && json.endsWith("]")) {
                sb.append(json, 1, json.length() - 1);
            } else {
                sb.append(json);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private record ToolResult(String type, String json) {}
}
