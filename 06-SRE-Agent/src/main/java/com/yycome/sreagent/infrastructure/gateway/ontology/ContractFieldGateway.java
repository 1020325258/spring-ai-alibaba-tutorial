package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * ContractField 实体的数据网关
 * <p>
 * 通过 HTTP 接口查询合同扩展字段（分表路由已在服务端 ContractFieldService 处理）
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号
 * - fields: 动态字段Map，键为字段名，值为字段值
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractFieldGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractField";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractFieldGateway] queryByField: {} = {}", fieldName, value);
        if (!"contractCode".equals(fieldName)) {
            throw new IllegalArgumentException("ContractField 不支持字段: " + fieldName);
        }

        String contractCode = String.valueOf(value);

        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-field",
                    Map.of("contractCode", contractCode));
            if (json == null) {
                log.warn("[ContractFieldGateway] 查询合同扩展字段失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            // 解析动态字段
            Map<String, String> dynamicFields = parseFieldMap(json);

            // 按 YAML 定义的属性组装返回
            Map<String, Object> result = JsonMappingUtils.newOrderedMap();
            result.put("contractCode", contractCode);
            result.put("fields", new LinkedHashMap<>(dynamicFields));

            return List.of(result);
        } catch (Exception e) {
            log.warn("[ContractFieldGateway] 查询合同扩展字段失败", e);
            return Collections.emptyList();
        }
    }

    private Map<String, String> parseFieldMap(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isObject() && !data.isEmpty()) {
                return objectMapper.readValue(data.toString(), new TypeReference<>() {});
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            log.warn("[ContractFieldGateway] 解析响应失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
