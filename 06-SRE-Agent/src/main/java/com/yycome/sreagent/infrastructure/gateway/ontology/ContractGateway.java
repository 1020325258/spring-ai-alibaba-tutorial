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
 * Contract 实体的数据网关
 * <p>
 * 通过 HTTP 接口查询合同表（contract）
 * 支持按 contractCode 或 projectOrderId 查询
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号
 * - type: 合同类型
 * - status: 合同状态
 * - amount: 合同金额
 * - projectOrderId: 所属订单号
 * - platformInstanceId: 协议平台实例 ID
 * - ctime: 创建时间
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
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("projectOrderId", projectOrderId, "contractCode", ""));
            if (json == null) {
                log.warn("[ContractGateway] 查询订单合同列表失败, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }
            return parseContracts(json);
        } catch (Exception e) {
            log.warn("[ContractGateway] 查询订单合同列表失败", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> queryContractBase(String contractCode) {
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("contractCode", contractCode, "projectOrderId", ""));
            if (json == null) {
                log.warn("[ContractGateway] 查询合同基本信息失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }
            Map<String, Object> contract = parseContract(json);
            return contract != null ? List.of(contract) : List.of();
        } catch (Exception e) {
            log.warn("[ContractGateway] 查询合同基本信息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析合同数组
     */
    private List<Map<String, Object>> parseContracts(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    result.add(buildContractMap(item));
                }
            }
        } catch (Exception e) {
            log.warn("[ContractGateway] 解析响应失败: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 解析单个合同
     */
    private Map<String, Object> parseContract(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isObject() && !data.isEmpty()) {
                return buildContractMap(data);
            }
            return null;
        } catch (Exception e) {
            log.warn("[ContractGateway] 解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按 YAML 定义的属性组装返回
     */
    private Map<String, Object> buildContractMap(JsonNode data) {
        Map<String, Object> contract = JsonMappingUtils.newOrderedMap();
        contract.put("contractCode", JsonMappingUtils.getText(data, "contractCode"));
        contract.put("type", JsonMappingUtils.getInt(data, "type"));
        contract.put("status", JsonMappingUtils.getText(data, "status"));
        contract.put("amount", JsonMappingUtils.getDouble(data, "amount"));
        contract.put("projectOrderId", JsonMappingUtils.getText(data, "projectOrderId"));
        contract.put("platformInstanceId", JsonMappingUtils.getText(data, "platformInstanceId"));
        contract.put("ctime", JsonMappingUtils.formatDateTime(data, "ctime"));
        return contract;
    }
}
