package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order 实体的数据网关
 * Order 是虚拟实体（无独立表），返回订单基本信息用于关系展开
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderGateway implements EntityDataGateway {

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

        // Order 是虚拟实体，返回一条记录包含 projectOrderId
        // 后续 Order → Contract 关系展开会查询实际的合同数据
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectOrderId", value);
        return List.of(result);
    }
}
