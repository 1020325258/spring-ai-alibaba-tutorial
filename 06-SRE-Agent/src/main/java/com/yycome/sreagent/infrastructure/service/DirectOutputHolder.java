package com.yycome.sreagent.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 直接输出持有者
 * 用于存储数据查询类工具的结果，绕过 LLM 处理直接输出。
 * 支持收集多个工具结果，在流结束时聚合输出。
 *
 * 注意：使用 request-scoped 存储来支持跨线程访问（工具执行在不同线程）。
 */
@Component
public class DirectOutputHolder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 当前请求的请求ID */
    private static final AtomicReference<String> CURRENT_REQUEST_ID = new AtomicReference<>();

    /** 请求ID -> 结果列表的映射 */
    private static final ConcurrentHashMap<String, CopyOnWriteArrayList<ToolResult>> REQUEST_RESULTS = new ConcurrentHashMap<>();

    /** 请求ID -> OUTPUT 标记的映射 */
    private static final ConcurrentHashMap<String, String> REQUEST_OUTPUT = new ConcurrentHashMap<>();

    /**
     * 工具结果记录
     */
    public static class ToolResult {
        public final String toolName;
        public final String result;
        public final long timestamp;

        public ToolResult(String toolName, String result) {
            this.toolName = toolName;
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * 开始新请求，生成请求ID
     */
    public void startRequest() {
        String requestId = UUID.randomUUID().toString();
        CURRENT_REQUEST_ID.set(requestId);
        REQUEST_RESULTS.put(requestId, new CopyOnWriteArrayList<>());
        REQUEST_OUTPUT.remove(requestId); // 标记为无输出
    }

    /**
     * 获取当前请求ID
     */
    private String getCurrentRequestId() {
        return CURRENT_REQUEST_ID.get();
    }

    /**
     * 添加工具结果（收集模式）
     */
    public void addResult(String toolName, String result) {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return; // 没有活动的请求
        }
        CopyOnWriteArrayList<ToolResult> results = REQUEST_RESULTS.get(requestId);
        if (results != null) {
            results.add(new ToolResult(toolName, result));
            REQUEST_OUTPUT.put(requestId, "pending");
        }
    }

    /**
     * 获取所有收集的结果
     */
    public List<ToolResult> getResults() {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return new ArrayList<>();
        }
        CopyOnWriteArrayList<ToolResult> results = REQUEST_RESULTS.get(requestId);
        return results != null ? new ArrayList<>(results) : new ArrayList<>();
    }

    /**
     * 设置直接输出内容（覆盖）- 兼容旧逻辑
     */
    public void set(String output) {
        String requestId = getCurrentRequestId();
        if (requestId != null) {
            REQUEST_OUTPUT.put(requestId, output);
        }
    }

    /**
     * 仅当当前无内容时才写入（first-write-wins）- 兼容旧逻辑
     */
    public boolean setIfAbsent(String output) {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return false;
        }
        String current = REQUEST_OUTPUT.get(requestId);
        if (current == null) {
            REQUEST_OUTPUT.put(requestId, output);
            return true;
        }
        return false;
    }

    /**
     * 获取并清除直接输出内容
     */
    public String getAndClear() {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return null;
        }
        String result = REQUEST_OUTPUT.remove(requestId);
        REQUEST_RESULTS.remove(requestId);
        CURRENT_REQUEST_ID.set(null);
        return result;
    }

    /**
     * 检查是否有直接输出内容
     */
    public boolean hasOutput() {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return false;
        }
        String output = REQUEST_OUTPUT.get(requestId);
        CopyOnWriteArrayList<ToolResult> results = REQUEST_RESULTS.get(requestId);
        return output != null && results != null && !results.isEmpty();
    }

    /**
     * 清除直接输出内容
     */
    public void clear() {
        String requestId = CURRENT_REQUEST_ID.getAndSet(null);
        if (requestId != null) {
            REQUEST_RESULTS.remove(requestId);
            REQUEST_OUTPUT.remove(requestId);
        }
    }

    /**
     * 检查是否有多个工具结果需要聚合
     */
    public boolean hasMultipleResults() {
        String requestId = getCurrentRequestId();
        if (requestId == null) {
            return false;
        }
        CopyOnWriteArrayList<ToolResult> results = REQUEST_RESULTS.get(requestId);
        return results != null && results.size() > 1;
    }

    /**
     * 获取聚合后的输出结果
     * 如果有多个 ontologyQuery 结果，会按层级合并
     */
    public String getAggregatedOutput() {
        List<ToolResult> results = getResults();
        if (results.isEmpty()) {
            return "";
        }
        if (results.size() == 1) {
            return results.get(0).result;
        }
        // 多个结果：尝试聚合
        try {
            return aggregateOntologyQueryResults(results);
        } catch (Exception e) {
            // 聚合失败，按原样输出每个结果
            StringBuilder sb = new StringBuilder();
            for (ToolResult r : results) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(r.result);
            }
            return sb.toString();
        }
    }

    /**
     * 获取并清除聚合后的输出
     */
    public String getAndClearAggregated() {
        String result = getAggregatedOutput();
        clear();
        return result;
    }

    /**
     * 聚合多个 ontologyQuery 结果
     * 场景：先查 Order 获取合同列表，再查每个合同的签约单据和节点
     */
    @SuppressWarnings("unchecked")
    private String aggregateOntologyQueryResults(List<ToolResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        // 解析所有结果
        List<Map<String, Object>> parsedResults = new ArrayList<>();
        Map<String, Object> baseResult = null; // Order 查询结果
        List<Map<String, Object>> contractResults = new ArrayList<>(); // Contract 查询结果

        for (ToolResult r : results) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(r.result, Map.class);
                parsedResults.add(parsed);

                // 区分 Order 查询和 Contract 查询
                Object queryEntity = parsed.get("queryEntity");
                if ("Order".equals(queryEntity)) {
                    baseResult = parsed;
                } else if ("Contract".equals(queryEntity)) {
                    contractResults.add(parsed);
                }
            } catch (Exception e) {
                // 解析失败，跳过
            }
        }

        // 如果有 Order 结果，将 Contract 结果合并到对应合同
        if (baseResult != null && !contractResults.isEmpty()) {
            Object recordsObj = baseResult.get("records");
            if (recordsObj instanceof List) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) recordsObj;

                // 构建合同编号到详细结果的映射
                Map<String, Map<String, Object>> contractDetails = new HashMap<>();
                for (Map<String, Object> cr : contractResults) {
                    Object crRecords = cr.get("records");
                    if (crRecords instanceof List && !((List<?>) crRecords).isEmpty()) {
                        // 从 Contract 结果中提取关联数据
                        extractContractRelations(cr, contractDetails);
                    }
                }

                // 将关联数据合并到 Order 结果的合同中
                for (Map<String, Object> contract : records) {
                    String contractCode = (String) contract.get("contractCode");
                    if (contractCode != null && contractDetails.containsKey(contractCode)) {
                        contract.putAll(contractDetails.get(contractCode));
                    }
                }
            }

            try {
                return OBJECT_MAPPER.writeValueAsString(baseResult);
            } catch (Exception e) {
                return baseResult.toString();
            }
        }

        // 无法聚合，按原样输出第一个结果
        try {
            return OBJECT_MAPPER.writeValueAsString(parsedResults.get(0));
        } catch (Exception e) {
            return results.get(0).result;
        }
    }

    /**
     * 从 Contract 查询结果中提取关联数据
     */
    @SuppressWarnings("unchecked")
    private void extractContractRelations(Map<String, Object> contractResult,
                                          Map<String, Map<String, Object>> contractDetails) {
        String queryValue = (String) contractResult.get("queryValue");
        if (queryValue == null) return;

        // 获取所有非基础字段作为关联数据
        Map<String, Object> details = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : contractResult.entrySet()) {
            String key = entry.getKey();
            // 跳过基础字段
            if ("queryEntity".equals(key) || "queryValue".equals(key) || "records".equals(key)) {
                continue;
            }
            // 保留关联数据
            details.put(key, entry.getValue());
        }

        // 合并到已有的详情中
        if (!details.isEmpty()) {
            contractDetails.merge(queryValue, details, (oldVal, newVal) -> {
                Map<String, Object> merged = new LinkedHashMap<>(oldVal);
                merged.putAll(newVal);
                return merged;
            });
        }
    }
}
