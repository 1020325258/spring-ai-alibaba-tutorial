package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import com.yycome.sremate.trigger.agent.HttpEndpointTool;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Contract 版式数据网关（本体论版）
 * 查询流程：
 * 1. 根据 contractCode 查询 platformInstanceId
 * 2. 调用 contract-form-data HTTP 接口获取 form_id
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractFormGateway implements EntityDataGateway {

    private final ContractDao contractDao;
    private final HttpEndpointTool httpEndpointTool;
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

        if (!"contractCode".equals(fieldName)) {
            return Collections.emptyList();
        }

        String contractCode = String.valueOf(value);

        // Step 1: 查询 platformInstanceId
        Long instanceId = contractDao.findPlatformInstanceId(contractCode);
        if (instanceId == null) {
            log.warn("[ContractFormGateway] 未找到 platformInstanceId, contractCode={}", contractCode);
            return Collections.emptyList();
        }

        // Step 2: 调用 HTTP 接口获取 form_id
        try {
            String formData = httpEndpointTool.callPredefinedEndpoint("contract-form-data",
                    Map.of("instanceId", instanceId.toString()));

            // 解析并构建结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", contractCode);
            result.put("platformInstanceId", instanceId);
            result.put("formData", formData);

            return List.of(result);
        } catch (Exception e) {
            log.warn("[ContractFormGateway] 查询版式失败 contractCode={}: {}", contractCode, e.getMessage());
            return Collections.emptyList();
        }
    }
}
