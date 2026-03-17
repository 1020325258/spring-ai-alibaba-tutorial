package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import com.yycome.sremate.types.enums.ContractTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Contract 配置表数据网关（本体论版）
 * 查询 contract_city_company_info 配置表
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

    private final ContractDao contractDao;
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

        // Step 1: 查询合同配置字段
        Map<String, Object> contractFields = contractDao.fetchContractConfigFields(contractCode);
        if (contractFields == null) {
            log.warn("[ContractConfigGateway] 未找到合同, contractCode={}", contractCode);
            return Collections.emptyList();
        }

        String projectOrderId = String.valueOf(contractFields.get("projectOrderId"));
        String businessType = String.valueOf(contractFields.get("businessType"));
        String gbCode = String.valueOf(contractFields.get("gbCode"));
        String companyCode = String.valueOf(contractFields.get("companyCode"));
        String type = String.valueOf(contractFields.get("type"));

        // Step 2: 查询 contract_config_id
        String contractConfigId = contractDao.fetchContractConfigId(projectOrderId);
        if (contractConfigId == null || contractConfigId.isBlank()) {
            log.warn("[ContractConfigGateway] 未找到 contract_config_id, projectOrderId={}", projectOrderId);
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("error", "未找到 project_config_snap 记录");
            errorResult.put("projectOrderId", projectOrderId);
            return List.of(errorResult);
        }

        // Step 3: 取第一个版本号
        String[] versions = contractConfigId.split("_");
        int version = Integer.parseInt(versions[0]);

        // Step 4: 查询 contract_city_company_info
        List<Map<String, Object>> cityCompanyInfo = contractDao.fetchCityCompanyInfo(
                businessType, gbCode, companyCode, version, type);

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
        result.put("typeName", ContractTypeEnum.getNameByCode(Byte.parseByte(type)));
        result.put("signChannelType", 1);
        result.put("cityCompanyInfo", cityCompanyInfo);

        return List.of(result);
    }
}
