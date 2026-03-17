package com.yycome.sremate.domain.ontology.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SubOrder（S单）实体的数据网关
 * 通过 BudgetBill → SubOrder 关系按需查询，仅在用户主动询问S单时触发。
 * 引擎从 BudgetBill 记录的 billCode 字段传入，作为 quotationOrderNo 参数查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubOrderGateway implements EntityDataGateway {

    private final HttpEndpointTool httpEndpointTool;
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
        log.debug("[SubOrderGateway] queryByField: {} = {}", fieldName, value);

        String quotationOrderNo = String.valueOf(value);

        try {
            String rawJson = httpEndpointTool.callPredefinedEndpointRaw("sub-order-info",
                    Map.of("homeOrderNo", "",
                           "quotationOrderNo", quotationOrderNo,
                           "projectChangeNo", ""));

            if (rawJson == null) {
                log.warn("[SubOrderGateway] 接口无响应 quotationOrderNo={}", quotationOrderNo);
                return Collections.emptyList();
            }

            return parseSubOrders(rawJson);
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 查询S单失败 quotationOrderNo={}: {}", quotationOrderNo, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseSubOrders(String rawJson) {
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
                    result.add(subOrder);
                }
            }
        } catch (Exception e) {
            log.warn("[SubOrderGateway] 解析S单响应失败: {}", e.getMessage());
        }
        return result;
    }
}
