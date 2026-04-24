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
 * BudgetBill（报价单）实体的数据网关
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - projectOrderId: 订单号
 * - billCode: 报价单号
 * - billType: 报价单类型
 * - billTypeDesc: 报价单类型描述
 * - statusDesc: 状态描述
 * - originalBillCode: 原始报价单号
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

    /**
     * 解析报价单数据，合并 decorateBudgetList 和 personalBudgetList
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseBills(String billListJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(billListJson);

            // 合并两个数组
            List<JsonNode> bills = collectBills(root);

            for (JsonNode bill : bills) {
                Map<String, Object> item = JsonMappingUtils.newOrderedMap();
                item.put("projectOrderId", projectOrderId);
                item.put("billCode", JsonMappingUtils.getText(bill, "billCode"));
                item.put("billType", JsonMappingUtils.getText(bill, "billType"));
                item.put("billTypeDesc", JsonMappingUtils.getText(bill, "billTypeDesc"));
                item.put("statusDesc", JsonMappingUtils.getText(bill, "statusDesc"));
                item.put("originalBillCode", JsonMappingUtils.getText(bill, "originalBillCode"));
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("[BudgetBillGateway] 解析报价单失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 收集两个数组中的报价单
     */
    private List<JsonNode> collectBills(JsonNode root) {
        List<JsonNode> bills = new ArrayList<>();
        JsonNode decorateList = root.path("decorateBudgetList");
        if (decorateList.isArray()) {
            decorateList.forEach(bills::add);
        }
        JsonNode personalList = root.path("personalBudgetList");
        if (personalList.isArray()) {
            personalList.forEach(bills::add);
        }
        return bills;
    }
}
