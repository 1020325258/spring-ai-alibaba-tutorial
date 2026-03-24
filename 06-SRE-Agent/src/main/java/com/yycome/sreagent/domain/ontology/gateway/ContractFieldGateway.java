package com.yycome.sreagent.domain.ontology.gateway;

import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.dao.ContractDao;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ContractField 实体的数据网关
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractFieldGateway implements EntityDataGateway {

    private final ContractDao contractDao;
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
        // 返回字段数据（作为 Map 包装在 List 中）
        Map<String, Object> fields = contractDao.fetchFields((String) value);
        return List.of(fields);
    }
}
