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
 * FormalSignableOrderInfo（正签可签约S单）实体的数据网关
 * 正签可签约S单属于整个订单维度，直接通过 projectOrderId 调用 formalQuotation/list/v2 接口查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormalSignableOrderInfoGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;
    private final EntityRegistry entityRegistry;
    private final EntitySchemaMapper schemaMapper;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "FormalSignableOrderInfo";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        if (!"projectOrderId".equals(fieldName)) {
            log.warn("[FormalSignableOrderInfoGateway] 不支持的查询字段: {}", fieldName);
            return Collections.emptyList();
        }
        if (value == null) {
            log.warn("[FormalSignableOrderInfoGateway] projectOrderId 为 null，无法查询");
            return Collections.emptyList();
        }
        String projectOrderId = value.toString();
        if (projectOrderId.isEmpty() || "null".equals(projectOrderId)) {
            log.warn("[FormalSignableOrderInfoGateway] projectOrderId 为空，无法查询");
            return Collections.emptyList();
        }
        return querySignableOrders(projectOrderId);
    }

    private List<Map<String, Object>> querySignableOrders(String projectOrderId) {
        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw("formal-sign-order-list",
                    Map.of("projectOrderId", projectOrderId));
            if (rawJson == null) {
                log.warn("[FormalSignableOrderInfoGateway] 接口无响应, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }

            // YAML 驱动的新解析方法
            List<Map<String, Object>> newResult = parseSignableOrdersNew(rawJson, projectOrderId);

            // 如果有配置，对比旧方法验证一致性
            OntologyEntity entity = entityRegistry.getEntity("FormalSignableOrderInfo");
            if (entity != null && entity.getAttributes() != null) {
                boolean hasSourceConfig = entity.getAttributes().stream()
                        .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

                if (hasSourceConfig) {
                    List<Map<String, Object>> oldResult = parseSignableOrdersOld(rawJson, projectOrderId);
                    if (!equals(newResult, oldResult)) {
                        log.error("[FormalSignableOrderInfoGateway] 新旧方法输出一致性校验失败! newResult={}, oldResult={}",
                                newResult, oldResult);
                    }
                }
            }

            return newResult;
        } catch (Exception e) {
            log.warn("[FormalSignableOrderInfoGateway] 查询正签可签约S单失败", e);
            return Collections.emptyList();
        }
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

                // 数值比较（处理 Integer/Double 类型差异）
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

    /**
     * YAML 驱动的新解析方法
     */
    private List<Map<String, Object>> parseSignableOrdersNew(String rawJson, String projectOrderId) {
        OntologyEntity entity = entityRegistry.getEntity("FormalSignableOrderInfo");
        if (entity == null || entity.getAttributes() == null) {
            log.warn("[FormalSignableOrderInfoGateway] 未找到实体定义，使用旧方法");
            return parseSignableOrdersOld(rawJson, projectOrderId);
        }

        // 检查是否有 source 配置
        boolean hasSourceConfig = entity.getAttributes().stream()
                .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

        if (!hasSourceConfig) {
            return parseSignableOrdersOld(rawJson, projectOrderId);
        }

        Map<String, Object> queryParams = Map.of("projectOrderId", projectOrderId);
        return schemaMapper.map(entity, rawJson, queryParams);
    }

    /**
     * 旧解析方法（保留用于一致性验证）
     * @deprecated 使用 {@link #parseSignableOrdersNew(String, String)} 代替
     */
    @Deprecated
    private List<Map<String, Object>> parseSignableOrdersOld(String rawJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                // data[] 是公司分组，真正的S单在 signableOrderInfos[] 里
                for (JsonNode companyGroup : data) {
                    String companyName = companyGroup.path("companyName").asText(null);
                    String companyCode = companyGroup.path("companyCode").asText(null);
                    JsonNode infos = companyGroup.path("signableOrderInfos");
                    if (infos.isArray()) {
                        for (JsonNode item : infos) {
                            Map<String, Object> order = new LinkedHashMap<>();
                            order.put("projectOrderId", projectOrderId);
                            order.put("companyName", companyName);
                            order.put("companyCode", companyCode);
                            order.put("goodsInfo", item.path("goodsInfo").asText(null));
                            order.put("orderAmount", item.path("orderAmount").asDouble());
                            order.put("orderCreateTime", item.path("orderCreateTime").asText(null));
                            order.put("bindType", item.path("bindType").asInt());
                            order.put("bindCode", item.path("bindCode").asText(null));
                            order.put("packageInstanceName", item.path("packageInstanceName").asText(null));
                            order.put("mustSelect", item.path("mustSelect").asBoolean());
                            result.add(order);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FormalSignableOrderInfoGateway] 解析正签可签约S单响应失败", e);
        }
        return result;
    }
}
