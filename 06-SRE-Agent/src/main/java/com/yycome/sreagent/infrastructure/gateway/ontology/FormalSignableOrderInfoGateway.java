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
 * FormalSignableOrderInfo（正签可签约S单）实体的数据网关
 * <p>
 * 正签可签约S单属于整个订单维度，直接通过 projectOrderId 调用 formalQuotation/list/v2 接口查询
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - projectOrderId: 订单号
 * - companyName: 公司名称
 * - companyCode: 公司编码
 * - goodsInfo: 商品信息
 * - orderAmount: 订单金额
 * - orderCreateTime: 订单创建时间
 * - bindType: 绑定类型
 * - bindCode: 绑定编码
 * - packageInstanceName: 套餐实例名称
 * - mustSelect: 是否必选
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormalSignableOrderInfoGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

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

            return parseSignableOrders(rawJson, projectOrderId);
        } catch (Exception e) {
            log.warn("[FormalSignableOrderInfoGateway] 查询正签可签约S单失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析正签可签约 S 单数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseSignableOrders(String rawJson, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                // data[] 是公司分组，真正的S单在 signableOrderInfos[] 里
                for (JsonNode companyGroup : data) {
                    String companyName = JsonMappingUtils.getText(companyGroup, "companyName");
                    String companyCode = JsonMappingUtils.getText(companyGroup, "companyCode");
                    JsonNode infos = companyGroup.path("signableOrderInfos");
                    if (infos.isArray()) {
                        for (JsonNode item : infos) {
                            Map<String, Object> order = JsonMappingUtils.newOrderedMap();
                            order.put("projectOrderId", projectOrderId);
                            order.put("companyName", companyName);
                            order.put("companyCode", companyCode);
                            order.put("goodsInfo", JsonMappingUtils.getText(item, "goodsInfo"));
                            order.put("orderAmount", JsonMappingUtils.getText(item, "orderAmount"));
                            order.put("orderCreateTime", JsonMappingUtils.getText(item, "orderCreateTime"));
                            order.put("bindType", JsonMappingUtils.getInt(item, "bindType"));
                            order.put("bindCode", JsonMappingUtils.getText(item, "bindCode"));
                            order.put("packageInstanceName", JsonMappingUtils.getText(item, "packageInstanceName"));
                            order.put("mustSelect", JsonMappingUtils.getInt(item, "mustSelect"));
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
