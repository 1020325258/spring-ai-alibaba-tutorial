package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.JsonMappingUtils;
import com.yycome.sreagent.types.enums.ContractTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Contract 配置表数据网关
 * <p>
 * 通过 HTTP 接口查询 contract_city_company_info 配置表
 * <p>
 * 查询流程：
 * 1. 根据 contractCode 获取 projectOrderId、businessType、gbCode、companyCode、type
 * 2. 查询 project_config_snap 获取 contract_config_id
 * 3. 取第一个版本号
 * 4. 查询 contract_city_company_info
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号
 * - projectOrderId: 所属订单号
 * - contractConfigId: 配置快照ID
 * - version: 配置版本号
 * - businessType: 业务类型
 * - gbCode: 区域编码
 * - companyCode: 公司编码
 * - type: 合同类型
 * - typeName: 合同类型名称
 * - cityCompanyInfo: 城市公司配置列表
 * - signChannelType: 签约渠道类型
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
            // Step 1: 查询合同配置字段
            String contractJson = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("contractCode", contractCode, "projectOrderId", ""));
            if (contractJson == null) {
                log.warn("[ContractConfigGateway] 查询合同配置字段失败, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            JsonNode contractData = parseDataNode(contractJson);
            if (contractData == null || contractData.isEmpty()) {
                log.warn("[ContractConfigGateway] 未找到合同, contractCode={}", contractCode);
                return Collections.emptyList();
            }

            String projectOrderId = JsonMappingUtils.getText(contractData, "projectOrderId");
            String businessType = JsonMappingUtils.getText(contractData, "businessType");
            String gbCode = JsonMappingUtils.getText(contractData, "gbCode");
            String companyCode = JsonMappingUtils.getText(contractData, "companyCode");
            Integer type = JsonMappingUtils.getInt(contractData, "type");

            // Step 2: 查询 project_config_snap
            String configSnapJson = httpEndpointClient.callPredefinedEndpointRaw("sre-project-config-snap",
                    Map.of("projectOrderId", projectOrderId));
            if (configSnapJson == null) {
                log.warn("[ContractConfigGateway] 查询配置快照失败, projectOrderId={}", projectOrderId);
                return Collections.emptyList();
            }

            JsonNode snapData = parseDataNode(configSnapJson);
            if (snapData == null || snapData.isEmpty()) {
                log.warn("[ContractConfigGateway] 未找到 project_config_snap 记录, projectOrderId={}", projectOrderId);
                return buildErrorResult("未找到 project_config_snap 记录", projectOrderId);
            }

            String contractConfigId = JsonMappingUtils.getText(snapData, "contractConfigId");
            if (contractConfigId == null || contractConfigId.isEmpty() || "null".equals(contractConfigId)) {
                log.warn("[ContractConfigGateway] contract_config_id 为空, projectOrderId={}", projectOrderId);
                return buildErrorResult("contract_config_id 为空", projectOrderId);
            }

            // Step 3: 取第一个版本号
            String[] versions = contractConfigId.split("_");
            int version = Integer.parseInt(versions[0]);

            // Step 4: 查询 contract_city_company_info
            String cityCompanyInfoJson = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-city-company-info",
                    Map.of(
                            "businessType", businessType != null ? businessType : "",
                            "gbCode", gbCode != null ? gbCode : "",
                            "companyCode", companyCode != null ? companyCode : "",
                            "version", String.valueOf(version),
                            "type", type != null ? String.valueOf(type) : ""
                    ));
            if (cityCompanyInfoJson == null) {
                log.warn("[ContractConfigGateway] 查询城市公司配置失败");
                return Collections.emptyList();
            }

            List<Map<String, Object>> cityCompanyInfo = parseDataArray(cityCompanyInfoJson);

            // 构建结果
            Map<String, Object> result = JsonMappingUtils.newOrderedMap();
            result.put("contractCode", contractCode);
            result.put("projectOrderId", projectOrderId);
            result.put("contractConfigId", contractConfigId);
            result.put("version", version);
            result.put("businessType", businessType);
            result.put("gbCode", gbCode);
            result.put("companyCode", companyCode);
            result.put("type", type);
            result.put("typeName", type != null ? ContractTypeEnum.getNameByCode(type.byteValue()) : null);
            result.put("signChannelType", 1);
            result.put("cityCompanyInfo", cityCompanyInfo);

            return List.of(result);
        } catch (Exception e) {
            log.warn("[ContractConfigGateway] 查询失败", e);
            return Collections.emptyList();
        }
    }

    private JsonNode parseDataNode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            return data.isObject() ? data : objectMapper.missingNode();
        } catch (Exception e) {
            log.warn("[ContractConfigGateway] 解析响应失败: {}", e.getMessage());
            return objectMapper.missingNode();
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

    private List<Map<String, Object>> buildErrorResult(String error, String projectOrderId) {
        Map<String, Object> errorResult = JsonMappingUtils.newOrderedMap();
        errorResult.put("error", error);
        errorResult.put("projectOrderId", projectOrderId);
        return List.of(errorResult);
    }
}
