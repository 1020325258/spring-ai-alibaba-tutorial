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

            return parseBills(billListJson, projectOrderId);
        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 查询报价单失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseBills(String billListJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(billListJson);

            for (JsonNode bill : collectBills(root)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("billCode",          bill.path("billCode").asText(null));
                item.put("billType",          bill.path("billType").asText(null));
                item.put("billTypeDesc",      bill.path("billTypeDesc").asText(null));
                item.put("statusDesc",        bill.path("statusDesc").asText(null));
                item.put("originalBillCode",  bill.path("originalBillCode").asText(null));
                item.put("projectOrderId",    projectOrderId);
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
}
