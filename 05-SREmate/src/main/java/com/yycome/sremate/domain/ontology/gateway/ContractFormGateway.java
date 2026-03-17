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
 * Contract 版式数据网关（本体论版）
 * 引擎从 Contract 记录中取 platformInstanceId，直接传入 queryByField。
 * instanceId 为 0 时表示合同尚未生成，直接返回提示信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractFormGateway implements EntityDataGateway {

    private final HttpEndpointTool httpEndpointTool;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractForm";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractFormGateway] queryByField: {} = {}", fieldName, value);

        String instanceId = String.valueOf(value);

        // instanceId 为 0 表示合同尚未在协议平台生成，无版式数据
        if ("0".equals(instanceId) || "null".equals(instanceId)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceId", instanceId);
            result.put("message", "合同未生成，无法查询对应协议平台版式信息");
            return List.of(result);
        }

        try {
            String rawJson = httpEndpointTool.callPredefinedEndpointRaw("contract-form-data",
                    Map.of("instanceId", instanceId));

            if (rawJson == null) {
                log.warn("[ContractFormGateway] 接口无响应 instanceId={}", instanceId);
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
            log.warn("[ContractFormGateway] 查询版式失败 instanceId={}: {}", instanceId, e.getMessage());
            return Collections.emptyList();
        }
    }
}
