package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.types.enums.ContractTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Contract 配置表数据网关（本体论版）
 * 通过 HTTP 接口查询 contract_city_company_info 配置表
 *
 * 查询流程：
 * 1. 根据 contractCode 获取 projectOrderId、businessType、gbCode、companyCode、type
 * 2. 查询 project_config_snap 获取 contract_config_id
 * 3. 取第一个版本号
 * 4. 查询 contract_city_company_info
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractConfigGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractConfig";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractConfigGateway] queryByField: {} = {}", fieldName, value);

        if (!"contractCode".equals(fieldName)) {
            return Collections.emptyList();
        }

        String contractCode = String.valueOf(value);

        try {
            // Step 1: 查询合同配置字段（复用 sre-contract 接口）
            String contractJson = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("contractCode", contractCode, "projectOrderId", ""));
            if (contractJson == null) {
                log.warn("[ContractConfigGateway] 查询合同配置字段失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            Map<String, Object> contractFields = parseDataObject(contractJson);
            if (contractFields == null || contractFields.isEmpty()) {
                log.warn("[ContractConfigGateway] 未找到合同, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            String projectOrderId = getString(contractFields, "projectOrderId");
            Object businessType = contractFields.get("businessType");
            Object gbCode = contractFields.get("gbCode");
            Object companyCode = contractFields.get("companyCode");
            Object type = contractFields.get("type");

            // Step 2: 查询 project_config_snap
            String configSnapJson = httpEndpointClient.callPredefinedEndpointRaw("sre-project-config-snap",
                    Map.of("projectOrderId", projectOrderId));
            if (configSnapJson == null) {
                log.warn("[ContractConfigGateway] 查询配置快照失败, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }

            Map<String, Object> snapData = parseDataObject(configSnapJson);
            if (snapData == null || snapData.isEmpty()) {
                log.warn("[ContractConfigGateway] 未找到 project_config_snap 记录, projectOrderId={}", projectOrderId);
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("error", "未找到 project_config_snap 记录");
                errorResult.put("projectOrderId", projectOrderId);
                return List.of(errorResult);
            }

            String contractConfigId = getString(snapData, "contractConfigId");
            if (contractConfigId == null || contractConfigId.isEmpty() || "null".equals(contractConfigId)) {
                log.warn("[ContractConfigGateway] contract_config_id 为空, projectOrderId={}", projectOrderId);
                Map<String, Object> errorResult = new LinkedHashMap<>();
                errorResult.put("error", "contract_config_id 为空");
                errorResult.put("projectOrderId", projectOrderId);
                return List.of(errorResult);
            }

            // Step 3: 取第一个版本号
            String[] versions = contractConfigId.split("_");
            int version = Integer.parseInt(versions[0]);

            // Step 4: 查询 contract_city_company_info
            String cityCompanyInfoJson = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-city-company-info",
                    Map.of(
                            "businessType", String.valueOf(businessType),
                            "gbCode", String.valueOf(gbCode),
                            "companyCode", String.valueOf(companyCode),
                            "version", String.valueOf(version),
                            "type", String.valueOf(type)
                    ));
            if (cityCompanyInfoJson == null) {
                log.warn("[ContractConfigGateway] 查询城市公司配置失败");
                return Collections.emptyList();
            }

            List<Map<String, Object>> cityCompanyInfo = parseDataArray(cityCompanyInfoJson);

            // 构建结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("projectOrderId", projectOrderId);
            result.put("contractConfigId", contractConfigId);
            result.put("version", version);
            result.put("businessType", businessType);
            result.put("gbCode", gbCode);
            result.put("companyCode", companyCode);
            result.put("type", type);
            result.put("typeName", ContractTypeEnum.getNameByCode(Byte.parseByte(String.valueOf(type))));
            result.put("signChannelType", 1);
            result.put("cityCompanyInfo", cityCompanyInfo);

            return List.of(result);
        } catch (Exception e) {
            log.warn("[ContractConfigGateway] 查询失败", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> parseDataObject(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isObject() && !data.isEmpty()) {
                return objectMapper.readValue(data.toString(), new TypeReference<>() {});
            }
            return null;
        } catch (Exception e) {
            log.warn("[ContractConfigGateway] 解析响应失败: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> parseDataArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                return objectMapper.readValue(data.toString(), new TypeReference<>() {});
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("[ContractConfigGateway] 解析响应失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
