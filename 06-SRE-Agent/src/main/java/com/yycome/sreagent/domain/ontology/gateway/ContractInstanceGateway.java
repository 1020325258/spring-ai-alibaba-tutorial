package com.yycome.sreagent.domain.ontology.gateway;

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
 * ContractInstance 实例数据网关（本体论版）
 * 引擎从 Contract 记录中取 platformInstanceId，直接传入 queryByField。
 * instanceId 为 0 时表示合同尚未生成，直接返回提示信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractInstanceGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractInstance";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractInstanceGateway] queryByField: {} = {}", fieldName, value);

        String instanceId = String.valueOf(value);

        // instanceId 为 0 表示合同尚未在协议平台生成，无版式数据
        if ("0".equals(instanceId) || "null".equals(instanceId)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceId", instanceId);
            result.put("message", "合同未生成，无法查询对应协议平台实例信息");
            return List.of(result);
        }

        try {
            String rawJson = httpEndpointClient.callPredefinedEndpointRaw("contract-form-data",
                    Map.of("instanceId", instanceId));

            if (rawJson == null) {
                log.warn("[ContractInstanceGateway] 接口无响应 instanceId={}", instanceId);
                return Collections.emptyList();
            }

            // 解析 data 字段
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceId", instanceId);
            if (!data.isNull() && !data.isMissingNode()) {
                result.put("formData", objectMapper.convertValue(data, Map.class));
            } else {
                result.put("formData", null);
            }
            return List.of(result);
        } catch (Exception e) {
            log.warn("[ContractInstanceGateway] 查询实例失败 instanceId={}: {}", instanceId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
