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
 * SubOrder（S单）实体的数据网关
 * 支持两种查询路径：
 * 1. BudgetBill → SubOrder：从父记录获取 homeOrderNo + quotationOrderNo
 * 2. Order → SubOrder：直接按订单号查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubOrderGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;
    private final EntitySchemaMapper schemaMapper;
    private final EntityRegistry entityRegistry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "SubOrder";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.warn("[SubOrderGateway] 缺少父记录上下文，无法查询S单");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryByFieldWithContext(String fieldName, Object value, Map<String, Object> parentRecord) {
        log.debug("[SubOrderGateway] queryByFieldWithContext: {} = {}, parentRecord keys: {}",
                fieldName, value, parentRecord != null ? parentRecord.keySet() : "null");

        if ("homeOrderNo".equals(fieldName)) {
            return queryByHomeOrderNo(fieldName, value, parentRecord);
        } else {
            return queryFromBudgetBill(fieldName, value, parentRecord);
        }
    }

    /**
     * 从 Order 直查 S 单
     */
    private List<Map<String, Object>> queryByHomeOrderNo(String fieldName, Object value, Map<String, Object> parentRecord) {
        String homeOrderNo = String.valueOf(value);

        if (homeOrderNo.isEmpty() || "null".equals(homeOrderNo)) {
            log.warn("[SubOrderGateway] homeOrderNo 为空，无法查询S单");
            return Collections.emptyList();
        }

        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", homeOrderNo,
                           "quotationOrderNo", "",
                           "projectChangeNo", ""));

            if (rawJson == null) {
                log.warn("[SubOrderGateway] 接口无响应 homeOrderNo={}", homeOrderNo);
                return Collections.emptyList();
            }

            List<Map<String, Object>> newResult = parseSubOrdersNew(rawJson);

            // 一致性校验
            consistencyCheck(newResult, rawJson);

            return newResult;
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 查询S单失败 homeOrderNo={}: {}", homeOrderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 BudgetBill 查 S 单
     */
    private List<Map<String, Object>> queryFromBudgetBill(String fieldName, Object value, Map<String, Object> parentRecord) {
        String quotationOrderNo = String.valueOf(value);
        String homeOrderNo = parentRecord != null
                ? String.valueOf(parentRecord.getOrDefault("projectOrderId", ""))
                : "";

        if (homeOrderNo.isEmpty() || "null".equals(homeOrderNo)) {
            log.warn("[SubOrderGateway] 父记录缺少 projectOrderId，无法查询S单");
            return Collections.emptyList();
        }

        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", homeOrderNo,
                           "quotationOrderNo", quotationOrderNo,
                           "projectChangeNo", ""));

            if (rawJson == null) {
                log.warn("[SubOrderGateway] 接口无响应 homeOrderNo={}, quotationOrderNo={}", homeOrderNo, quotationOrderNo);
                return Collections.emptyList();
            }

            List<Map<String, Object>> newResult = parseSubOrdersNew(rawJson);

            // 一致性校验
            consistencyCheck(newResult, rawJson);

            return newResult;
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 查询S单失败 homeOrderNo={}, quotationOrderNo={}: {}",
                    homeOrderNo, quotationOrderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * YAML 驱动的新解析方法
     */
    private List<Map<String, Object>> parseSubOrdersNew(String rawJson) {
        OntologyEntity entity = entityRegistry.getEntity("SubOrder");
        if (entity == null || entity.getAttributes() == null) {
            log.warn("[SubOrderGateway] 未找到实体定义，使用旧方法");
            return parseSubOrdersOld(rawJson);
        }

        boolean hasSourceConfig = entity.getAttributes().stream()
                .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

        if (!hasSourceConfig) {
            return parseSubOrdersOld(rawJson);
        }

        return schemaMapper.map(entity, rawJson, Collections.emptyMap());
    }

    /**
     * 一致性校验
     */
    private void consistencyCheck(List<Map<String, Object>> newResult, String rawJson) {
        OntologyEntity entity = entityRegistry.getEntity("SubOrder");
        if (entity != null && entity.getAttributes() != null) {
            boolean hasSourceConfig = entity.getAttributes().stream()
                    .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

            if (hasSourceConfig) {
                List<Map<String, Object>> oldResult = parseSubOrdersOld(rawJson);
                if (!equals(newResult, oldResult)) {
                    log.error("[SubOrderGateway] 新旧方法输出一致性校验失败! newResult={}, oldResult={}",
                            newResult, oldResult);
                }
            }
        }
    }

    /**
     * 旧解析方法（保留用于一致性校验）
     * @deprecated 使用 parseSubOrdersNew 代替
     */
    @Deprecated
    private List<Map<String, Object>> parseSubOrdersOld(String rawJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode item : data) {
                    Map<String, Object> subOrder = new LinkedHashMap<>();
                    subOrder.put("orderNo",         item.path("orderNo").asText(null));
                    subOrder.put("projectChangeNo", item.path("projectChangeNo").asText(null));
                    subOrder.put("mdmCode",         item.path("mdmCode").asText(null));
                    subOrder.put("dueAmount",       item.path("dueAmount").asText(null));
                    subOrder.put("status",          item.path("status").asText(null));
                    result.add(subOrder);
                }
            }
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 解析S单响应失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 比较两个结果列表是否相等
     */
    private boolean equals(List<Map<String, Object>> list1, List<Map<String, Object>> list2) {
        if (list1 == null && list2 == null) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;

        for (int i = 0; i < list1.size(); i++) {
            Map<String, Object> map1 = list1.get(i);
            Map<String, Object> map2 = list2.get(i);

            if (map1.size() != map2.size()) return false;

            for (String key : map1.keySet()) {
                Object val1 = map1.get(key);
                Object val2 = map2.get(key);

                if (val1 == null && val2 == null) continue;
                if (val1 == null || val2 == null) return false;

                if (val1 instanceof Number && val2 instanceof Number) {
                    if (((Number) val1).doubleValue() != ((Number) val2).doubleValue()) {
                        return false;
                    }
                } else if (!val1.equals(val2)) {
                    return false;
                }
            }
        }
        return true;
    }
}
