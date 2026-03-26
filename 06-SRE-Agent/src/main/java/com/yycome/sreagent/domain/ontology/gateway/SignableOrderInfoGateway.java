package com.yycome.sreagent.domain.ontology.gateway;

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
 * SignableOrderInfo（弹窗可签约S单）实体的数据网关
 * 从父记录（Contract）获取 type 和 projectOrderId，根据合同类型路由到不同接口：
 * - type=8（销售合同）→ sign-order-list 端点
 * - type=3（正签合同）→ 对应端点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignableOrderInfoGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    // 合同类型常量
    private static final String CONTRACT_TYPE_SALES = "8";  // 销售合同
    private static final String CONTRACT_TYPE_FORMAL = "3"; // 正签合同

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "SignableOrderInfo";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        // 无父记录上下文时，无法查询（需要从 Contract 获取 type 和 projectOrderId）
        log.warn("[SignableOrderInfoGateway] 缺少父记录上下文，无法查询弹窗S单");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryByFieldWithContext(String fieldName, Object value, Map<String, Object> parentRecord) {
        log.debug("[SignableOrderInfoGateway] queryByFieldWithContext: fieldName={}, value={}, parentRecord keys={}",
                fieldName, value, parentRecord != null ? parentRecord.keySet() : "null");

        if (parentRecord == null) {
            log.warn("[SignableOrderInfoGateway] 父记录为空，无法查询弹窗S单");
            return Collections.emptyList();
        }

        // 从父记录（Contract）获取 type 和 projectOrderId
        String contractType = String.valueOf(parentRecord.getOrDefault("type", ""));
        String projectOrderId = String.valueOf(parentRecord.getOrDefault("projectOrderId", ""));

        if (projectOrderId.isEmpty() || "null".equals(projectOrderId)) {
            log.warn("[SignableOrderInfoGateway] 父记录缺少 projectOrderId，无法查询弹窗S单");
            return Collections.emptyList();
        }

        log.info("[SignableOrderInfoGateway] contractType={}, projectOrderId={}", contractType, projectOrderId);

        // 根据合同类型路由到不同接口
        String endpointId;
        if (CONTRACT_TYPE_SALES.equals(contractType)) {
            // 销售合同 type=8 → sign-order-list
            endpointId = "sign-order-list";
        } else if (CONTRACT_TYPE_FORMAL.equals(contractType)) {
            // 正签合同 type=3 → TODO: 需要确认具体端点，目前先返回空列表
            log.warn("[SignableOrderInfoGateway] 正签合同(type=3)暂未配置对应端点");
            return Collections.emptyList();
        } else {
            // 未知合同类型
            log.warn("[SignableOrderInfoGateway] 未知合同类型: {}, 返回空列表", contractType);
            return Collections.emptyList();
        }

        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw(endpointId,
                    Map.of("projectOrderId", projectOrderId));

            if (rawJson == null) {
                log.warn("[SignableOrderInfoGateway] 接口无响应, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }

            return parseSignableOrders(rawJson, projectOrderId, contractType);
        } catch (Exception e) {
            log.warn("[SignableOrderInfoGateway] 查询弹窗S单失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseSignableOrders(String rawJson, String projectOrderId, String contractType) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode item : data) {
                    Map<String, Object> order = new LinkedHashMap<>();
                    order.put("orderNo", item.path("orderNo").asText(null));
                    order.put("projectOrderId", projectOrderId);
                    order.put("type", contractType);
                    order.put("status", item.path("status").asText(null));
                    order.put("statusDesc", item.path("statusDesc").asText(null));
                    result.add(order);
                }
            }
        } catch (Exception e) {
            log.warn("[SignableOrderInfoGateway] 解析弹窗S单响应失败: {}", e.getMessage());
        }
        return result;
    }
}