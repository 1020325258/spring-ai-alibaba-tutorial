package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.infrastructure.dao.ContractDao;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order 实体的数据网关
 * 负责根据订单号查询合同列表（作为 Order -> Contract 的桥梁）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderGateway implements EntityDataGateway {

    private final ContractDao contractDao;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "Order";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[OrderGateway] queryByField: {} = {}", fieldName, value);

        if (!"projectOrderId".equals(fieldName)) {
            throw new IllegalArgumentException("Order 不支持字段: " + fieldName);
        }

        // 查询该订单下的所有合同
        List<Map<String, Object>> contracts = contractDao.fetchContractsByOrderId((String) value);

        // 转换字段名为驼峰格式
        return contracts.stream().map(c -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contractCode", c.get("contract_code"));
            result.put("type", c.get("type"));
            result.put("status", c.get("status"));
            result.put("amount", c.get("amount"));
            result.put("platformInstanceId", c.get("platform_instance_id"));
            result.put("ctime", c.get("ctime"));
            return result;
        }).toList();
    }
}
