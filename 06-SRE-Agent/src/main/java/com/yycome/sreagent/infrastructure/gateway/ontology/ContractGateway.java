package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
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
 * Contract 实体的数据网关
 * 通过 HTTP 接口查询合同表（contract）
 * 支持按 contractCode 或 projectOrderId 查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "Contract";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractGateway] queryByField: {} = {}", fieldName, value);
        return switch (fieldName) {
            case "projectOrderId" -> queryContractsByOrderId((String) value);
            case "contractCode" -> queryContractBase((String) value);
            default -> throw new IllegalArgumentException("Contract 不支持字段: " + fieldName);
        };
    }

    private List<Map<String, Object>> queryContractsByOrderId(String projectOrderId) {
        try {
            // 调用 sre-contract 接口，传入 projectOrderId
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("projectOrderId", projectOrderId, "contractCode", ""));
            if (json == null) {
                log.warn("[ContractGateway] 查询订单合同列表失败, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }
            return parseDataArray(json);
        } catch (Exception e) {
            log.warn("[ContractGateway] 查询订单合同列表失败", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> queryContractBase(String contractCode) {
        try {
            // 调用 sre-contract 接口，传入 contractCode
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("contractCode", contractCode, "projectOrderId", ""));
            if (json == null) {
                log.warn("[ContractGateway] 查询合同基本信息失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }
            Map<String, Object> contract = parseDataObject(json);
            return contract != null ? List.of(contract) : List.of();
        } catch (Exception e) {
            log.warn("[ContractGateway] 查询合同基本信息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析 ResultDTO 格式的响应，提取 data 数组
     */
    private List<Map<String, Object>> parseDataArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                return objectMapper.readValue(data.toString(), new TypeReference<>() {});
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("[ContractGateway] 解析响应失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析 ResultDTO 格式的响应，提取 data 对象
     */
    private Map<String, Object> parseDataObject(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isObject() && !data.isEmpty()) {
                return objectMapper.readValue(data.toString(), new TypeReference<>() {});
            }
            return null;
        } catch (Exception e) {
            log.warn("[ContractGateway] 解析响应失败: {}", e.getMessage());
            return null;
        }
    }
}
