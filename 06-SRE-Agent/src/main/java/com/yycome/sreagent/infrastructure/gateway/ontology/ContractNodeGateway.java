package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.DateTimeUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ContractNode 实体的数据网关
 * 通过 HTTP 接口查询合同节点数据
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
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-node",
                    Map.of("contractCode", String.valueOf(value)));
            if (json == null) {
                log.warn("[ContractNodeGateway] 查询合同节点失败, contractCode={}", value);
                return Collections.emptyList();
            }
            return parseNodes(json);
        } catch (Exception e) {
            log.warn("[ContractNodeGateway] 查询合同节点失败", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseNodes(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode node : data) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("nodeType", node.path("nodeType").asInt());
                item.put("fireTime", DateTimeUtil.format(node.path("fireTime").asLong()));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[ContractNodeGateway] 解析响应失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
