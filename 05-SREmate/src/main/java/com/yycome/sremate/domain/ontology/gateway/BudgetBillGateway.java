package com.yycome.sremate.domain.ontology.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * BudgetBill（报价单）实体的数据网关
 * 通过 HTTP 接口查询报价单列表，并聚合子单数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetBillGateway implements EntityDataGateway {

    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

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
            // 1. 查询报价单列表
            String billListJson = httpEndpointTool.callPredefinedEndpoint("budget-bill-list",
                    Map.of("projectOrderId", projectOrderId));

            // 检查是否为错误响应
            if (billListJson != null && billListJson.contains("\"error\"")) {
                log.warn("[BudgetBillGateway] 报价单接口返回错误: {}", billListJson);
                return Collections.emptyList();
            }

            // 2. 解析并聚合子单数据
            return parseAndEnrichBudgetBills(billListJson, projectOrderId);
        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 查询报价单失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析报价单列表并聚合子单数据
     */
    private List<Map<String, Object>> parseAndEnrichBudgetBills(String billListJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            JsonNode billListNode = objectMapper.readTree(billListJson);

            // 处理装修报价单
            JsonNode decorateList = billListNode.path("decorateBudgetList");
            if (decorateList.isArray()) {
                for (JsonNode bill : decorateList) {
                    result.add(enrichBillWithSubOrders(bill, projectOrderId));
                }
            }

            // 处理个性化报价单
            JsonNode personalList = billListNode.path("personalBudgetList");
            if (personalList.isArray()) {
                for (JsonNode bill : personalList) {
                    result.add(enrichBillWithSubOrders(bill, projectOrderId));
                }
            }

        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 解析报价单失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 为单个报价单聚合子单数据
     */
    private Map<String, Object> enrichBillWithSubOrders(JsonNode bill, String projectOrderId) {
        Map<String, Object> enrichedBill = new LinkedHashMap<>();

        // 提取报价单基本信息
        enrichedBill.put("billCode", bill.path("billCode").asText(null));
        enrichedBill.put("billType", bill.path("billType").asText(null));
        enrichedBill.put("billTypeDesc", bill.path("billTypeDesc").asText(null));
        enrichedBill.put("statusDesc", bill.path("statusDesc").asText(null));
        enrichedBill.put("originalBillCode", bill.path("originalBillCode").asText(null));

        // 查询子单
        String billCode = bill.path("billCode").asText(null);
        List<Map<String, Object>> subOrders = querySubOrdersForBill(projectOrderId, billCode);
        enrichedBill.put("subOrders", subOrders);

        return enrichedBill;
    }

    /**
     * 查询报价单对应的子单列表
     */
    private List<Map<String, Object>> querySubOrdersForBill(String projectOrderId, String billCode) {
        List<Map<String, Object>> subOrders = new ArrayList<>();

        if (billCode == null || billCode.isBlank()) {
            return subOrders;
        }

        try {
            String raw = httpEndpointTool.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", projectOrderId,
                           "quotationOrderNo", billCode,
                           "projectChangeNo", ""));

            if (raw == null) {
                return subOrders;
            }

            JsonNode data = objectMapper.readTree(raw).path("data");
            if (!data.isArray()) {
                return subOrders;
            }

            for (JsonNode item : data) {
                Map<String, Object> subOrder = new LinkedHashMap<>();
                subOrder.put("orderNo", item.path("orderNo").asText(null));
                subOrder.put("projectChangeNo", item.path("projectChangeNo").asText(null));
                subOrder.put("mdmCode", item.path("mdmCode").asText(null));

                // dueAmount 可选
                if (!item.path("dueAmount").isMissingNode()) {
                    subOrder.put("dueAmount", item.path("dueAmount").asDouble(0));
                }

                subOrders.add(subOrder);
            }

        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 查询子单失败 billCode={}: {}", billCode, e.getMessage());
        }

        return subOrders;
    }
}
