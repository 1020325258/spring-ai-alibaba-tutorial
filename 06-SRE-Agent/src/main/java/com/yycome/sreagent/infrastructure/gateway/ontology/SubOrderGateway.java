package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.JsonMappingUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SubOrder（S单）实体的数据网关
 * <p>
 * 支持两种查询路径：
 * 1. BudgetBill → SubOrder：从父记录获取 homeOrderNo + quotationOrderNo
 * 2. Order → SubOrder：直接按订单号查询
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - orderNo: S单编号
 * - projectChangeNo: 变更单号
 * - mdmCode: MDM编码
 * - dueAmount: 应付金额
 * - status: S单状态
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubOrderGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

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

            return parseSubOrders(rawJson);
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 查询S单失败 homeOrderNo={}: {}", homeOrderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

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

            return parseSubOrders(rawJson);
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 查询S单失败 homeOrderNo={}, quotationOrderNo={}: {}",
                    homeOrderNo, quotationOrderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 S 单数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseSubOrders(String rawJson) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode item : data) {
                    Map<String, Object> subOrder = JsonMappingUtils.newOrderedMap();
                    subOrder.put("orderNo", JsonMappingUtils.getText(item, "orderNo"));
                    subOrder.put("projectChangeNo", JsonMappingUtils.getText(item, "projectChangeNo"));
                    subOrder.put("mdmCode", JsonMappingUtils.getText(item, "mdmCode"));
                    subOrder.put("dueAmount", JsonMappingUtils.getText(item, "dueAmount"));
                    subOrder.put("status", JsonMappingUtils.getText(item, "status"));
                    result.add(subOrder);
                }
            }
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 解析S单响应失败: {}", e.getMessage());
        }
        return result;
    }
}
