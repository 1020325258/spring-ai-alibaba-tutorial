package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.domain.ontology.engine.EntitySchemaMapper;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BudgetBill（报价单）实体的数据网关
 * 只返回报价单基本信息，不查询 S单。
 * S单通过 Order → SubOrder 关系按需查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetBillGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;
    private final EntityRegistry entityRegistry;
    private final EntitySchemaMapper entitySchemaMapper;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "BudgetBill";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[BudgetBillGateway] queryByField: {} = {}", fieldName, value);

        if (!"projectOrderId".equals(fieldName)) {
            throw new IllegalArgumentException("BudgetBill 不支持字段: " + fieldName);
        }

        String projectOrderId = String.valueOf(value);

        try {
            String billListJson = httpEndpointClient.callPredefinedEndpointRaw("budget-bill-list",
                    Map.of("projectOrderId", projectOrderId));

            if (billListJson != null && billListJson.contains("\"error\"")) {
                log.warn("[BudgetBillGateway] 报价单接口返回错误: {}", billListJson);
                return Collections.emptyList();
            }

            // 查询参数
            Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("projectOrderId", projectOrderId);

            // 从 EntityRegistry 获取实体定义
            OntologyEntity entity = entityRegistry.getEntity("BudgetBill");

            // 使用 YAML 驱动的新解析方法
            List<Map<String, Object>> newResult = entitySchemaMapper.map(entity, billListJson, queryParams);

            // 一致性校验：对比新旧方法输出
            if (entity != null && entity.getAttributes() != null) {
                boolean hasSourceConfig = entity.getAttributes().stream()
                        .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

                if (hasSourceConfig) {
                    List<Map<String, Object>> oldResult = parseBillsOld(billListJson, projectOrderId);
                    if (!equals(newResult, oldResult)) {
                        log.error("[BudgetBillGateway] 新旧方法输出一致性校验失败! newResult={}, oldResult={}",
                                newResult, oldResult);
                    }
                }
            }

            return newResult;
        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 查询报价单失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 旧解析方法（保留用于一致性校验）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseBillsOld(String billListJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(billListJson);

            for (JsonNode bill : collectBills(root)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("billCode", bill.path("billCode").asText(null));
                item.put("billType", bill.path("billType").asText(null));
                item.put("billTypeDesc", bill.path("billTypeDesc").asText(null));
                item.put("statusDesc", bill.path("statusDesc").asText(null));
                item.put("originalBillCode", bill.path("originalBillCode").asText(null));
                item.put("projectOrderId", projectOrderId);
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 解析报价单失败: {}", e.getMessage());
        }
        return result;
    }

    private List<JsonNode> collectBills(JsonNode root) {
        List<JsonNode> bills = new ArrayList<>();
        JsonNode decorateList = root.path("decorateBudgetList");
        if (decorateList.isArray()) decorateList.forEach(bills::add);
        JsonNode personalList = root.path("personalBudgetList");
        if (personalList.isArray()) personalList.forEach(bills::add);
        return bills;
    }

    /**
     * 新旧结果比较（支持 Number 类型比较）
     */
    private boolean equals(List<Map<String, Object>> newResult, List<Map<String, Object>> oldResult) {
        if (newResult == null && oldResult == null) return true;
        if (newResult == null || oldResult == null) return false;
        if (newResult.size() != oldResult.size()) return false;

        for (int i = 0; i < newResult.size(); i++) {
            Map<String, Object> newMap = newResult.get(i);
            Map<String, Object> oldMap = oldResult.get(i);
            if (!mapEquals(newMap, oldMap)) return false;
        }
        return true;
    }

    private boolean mapEquals(Map<String, Object> m1, Map<String, Object> m2) {
        if (m1.size() != m2.size()) return false;
        for (String key : m1.keySet()) {
            Object v1 = m1.get(key);
            Object v2 = m2.get(key);
            if (!valueEquals(v1, v2)) return false;
        }
        return true;
    }

    private boolean valueEquals(Object v1, Object v2) {
        if (v1 == v2) return true;
        if (v1 == null || v2 == null) return false;
        // 支持 Number 类型比较（如 Integer vs Double）
        if (v1 instanceof Number && v2 instanceof Number) {
            return ((Number) v1).doubleValue() == ((Number) v2).doubleValue();
        }
        return v1.equals(v2);
    }
}
