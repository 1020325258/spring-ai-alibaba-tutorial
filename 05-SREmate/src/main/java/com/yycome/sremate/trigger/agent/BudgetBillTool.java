package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 报价单查询工具
 * 负责：报价单列表及其子单聚合查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetBillTool {

    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;

    /**
     * 根据项目订单号查询报价单列表
     *
     * @param projectOrderId 项目订单号，纯数字格式
     * @return 过滤后的报价单列表 JSON（含子单聚合）
     */
    @Tool(description = """
            【报价单查询】用户提到"报价单"或"报价"时使用。

            触发条件：包含关键词"报价单"、"报价"、"报价列表"

            参数：
            - projectOrderId：纯数字订单号（必填）

            示例：
            - "826031111000001859的报价单" → projectOrderId=826031111000001859
            - "查询826031111000001859报价单列表" → projectOrderId=826031111000001859

            注意：报价单 ≠ 子单，不要用子单工具查报价单""")
    @DataQueryTool
    public String queryBudgetBillList(String projectOrderId) {
        return ToolExecutionTemplate.execute("queryBudgetBillList", () -> {
            // 1. 获取报价单列表（已过滤字段）
            String billListJson = httpEndpointTool.callPredefinedEndpoint("budget-bill-list",
                    Map.of("projectOrderId", projectOrderId));

            // 2. 逐条报价单查询子单并聚合
            JsonNode billListNode = objectMapper.readTree(billListJson);
            ObjectNode result = objectMapper.createObjectNode();

            for (String listKey : List.of("decorateBudgetList", "personalBudgetList")) {
                JsonNode list = billListNode.path(listKey);
                if (!list.isArray()) {
                    result.set(listKey, list);
                    continue;
                }
                ArrayNode enrichedList = objectMapper.createArrayNode();
                for (JsonNode bill : list) {
                    ObjectNode enrichedBill = (ObjectNode) bill.deepCopy();
                    String billCode = bill.path("billCode").asText(null);
                    enrichedBill.set("subOrders", querySubOrdersForBill(projectOrderId, billCode));
                    enrichedList.add(enrichedBill);
                }
                result.set(listKey, enrichedList);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 查询单条报价单对应的子单列表，提取 orderNo/projectChangeNo/mdmCode/dueAmount
     */
    private ArrayNode querySubOrdersForBill(String projectOrderId, String billCode) {
        ArrayNode subOrders = objectMapper.createArrayNode();
        if (billCode == null || billCode.isBlank()) return subOrders;
        try {
            String raw = httpEndpointTool.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", projectOrderId, "quotationOrderNo", billCode, "projectChangeNo", ""));
            if (raw == null) return subOrders;

            JsonNode data = objectMapper.readTree(raw).path("data");
            if (!data.isArray()) return subOrders;

            for (JsonNode item : data) {
                ObjectNode subOrder = objectMapper.createObjectNode();
                subOrder.set("orderNo", item.path("orderNo"));
                subOrder.set("projectChangeNo", item.path("projectChangeNo"));
                subOrder.set("mdmCode", item.path("mdmCode"));
                // dueAmount 不是所有接口版本都返回，缺失时不写入
                if (!item.path("dueAmount").isMissingNode()) {
                    subOrder.set("dueAmount", item.path("dueAmount"));
                }
                subOrders.add(subOrder);
            }
        } catch (Exception e) {
            log.warn("querySubOrdersForBill 失败 billCode={}: {}", billCode, e.getMessage());
        }
        return subOrders;
    }
}
