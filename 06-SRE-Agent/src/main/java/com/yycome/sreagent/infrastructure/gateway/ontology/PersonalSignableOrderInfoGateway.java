package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PersonalSignableOrderInfo（销售合同弹窗可签约S单）实体的数据网关
 * 可签约S单属于整个订单维度，直接通过 projectOrderId 调用 sign-order-list 接口查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalSignableOrderInfoGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "PersonalSignableOrderInfo";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        if (!"projectOrderId".equals(fieldName)) {
            log.warn("[PersonalSignableOrderInfoGateway] 不支持的查询字段: {}", fieldName);
            return Collections.emptyList();
        }
        if (value == null) {
            log.warn("[PersonalSignableOrderInfoGateway] projectOrderId 为 null，无法查询");
            return Collections.emptyList();
        }
        String projectOrderId = value.toString();
        if (projectOrderId.isEmpty() || "null".equals(projectOrderId)) {
            log.warn("[PersonalSignableOrderInfoGateway] projectOrderId 为空，无法查询");
            return Collections.emptyList();
        }
        return querySignableOrders(projectOrderId);
    }

    private List<Map<String, Object>> querySignableOrders(String projectOrderId) {
        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw("sign-order-list",
                    Map.of("projectOrderId", projectOrderId));
            if (rawJson == null) {
                log.warn("[PersonalSignableOrderInfoGateway] 接口无响应, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }
            return parseSignableOrders(rawJson, projectOrderId);
        } catch (Exception e) {
            log.warn("[PersonalSignableOrderInfoGateway] 查询弹窗S单失败", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseSignableOrders(String rawJson, String projectOrderId) {
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
            log.warn("[PersonalSignableOrderInfoGateway] 解析弹窗S单响应失败", e);
        }
        return result;
    }
}
