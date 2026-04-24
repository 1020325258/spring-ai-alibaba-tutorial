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
 * ContractNode 实体的数据网关
 * <p>
 * 通过 HTTP 接口查询合同节点数据
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号
 * - nodeType: 节点类型
 * - fireTime: 触发时间
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractNodeGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractNode";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractNodeGateway] queryByField: {} = {}", fieldName, value);
        if (!"contractCode".equals(fieldName)) {
            throw new IllegalArgumentException("ContractNode 不支持字段: " + fieldName);
        }

        String contractCode = String.valueOf(value);

        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-node",
                    Map.of("contractCode", contractCode));
            if (json == null) {
                log.warn("[ContractNodeGateway] 查询合同节点失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }
            return parseNodes(json, contractCode);
        } catch (Exception e) {
            log.warn("[ContractNodeGateway] 查询合同节点失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析合同节点数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseNodes(String json, String contractCode) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return result;
            }

            for (JsonNode node : data) {
                Map<String, Object> item = JsonMappingUtils.newOrderedMap();
                item.put("contractCode", contractCode);
                item.put("nodeType", JsonMappingUtils.getInt(node, "nodeType"));
                item.put("fireTime", JsonMappingUtils.formatDateTime(node, "fireTime"));
                result.add(item);
            }
        } catch (Exception e) {
            log.warn("[ContractNodeGateway] 解析响应失败: {}", e.getMessage());
        }
        return result;
    }
}
