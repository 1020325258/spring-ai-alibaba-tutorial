package com.yycome.sremate.domain.ontology.engine;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体数据网关注册表
 */
@Slf4j
@Component
public class EntityGatewayRegistry {

    private final Map<String, EntityDataGateway> gateways = new HashMap<>();

    public void register(EntityDataGateway gateway) {
        gateways.put(gateway.getEntityName(), gateway);
        log.info("[EntityGatewayRegistry] 注册网关: {}", gateway.getEntityName());
    }

    public EntityDataGateway getGateway(String entityName) {
        return gateways.get(entityName);
    }

    public boolean hasGateway(String entityName) {
        return gateways.containsKey(entityName);
    }
}
