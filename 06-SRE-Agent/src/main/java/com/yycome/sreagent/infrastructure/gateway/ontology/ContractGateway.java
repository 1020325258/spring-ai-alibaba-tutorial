package com.yycome.sreagent.infrastructure.gateway.ontology;

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
 * Contract 实体的数据网关
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractGateway implements EntityDataGateway {

    private final ContractDao contractDao;
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
            case "projectOrderId" -> contractDao.fetchContractsByOrderId((String) value);
            case "contractCode" -> {
                Map<String, Object> contract = contractDao.fetchContractBase((String) value);
                yield contract != null ? List.of(contract) : List.of();
            }
            default -> throw new IllegalArgumentException("Contract 不支持字段: " + fieldName);
        };
    }
}
