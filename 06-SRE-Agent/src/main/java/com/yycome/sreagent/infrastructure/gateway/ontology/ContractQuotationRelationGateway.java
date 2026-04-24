package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.DateTimeUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ContractQuotationRelation 实体的数据网关
 * 通过 HTTP 接口查询签约单据数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractQuotationRelationGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final ObjectMapper objectMapper;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "ContractQuotationRelation";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        log.debug("[ContractQuotationRelationGateway] queryByField: {} = {}", fieldName, value);
        if (!"contractCode".equals(fieldName)) {
            throw new IllegalArgumentException("ContractQuotationRelation 不支持字段: " + fieldName);
        }
        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract-quotation-relation",
                    Map.of("contractCode", String.valueOf(value)));
            if (json == null) {
                log.warn("[ContractQuotationRelationGateway] 查询签约单据失败, contractCode={}", value);
                return Collections.emptyList();
            }
            return parseQuotations(json);
        } catch (Exception e) {
            log.warn("[ContractQuotationRelationGateway] 查询签约单据失败", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parseQuotations(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode item : data) {
                Map<String, Object> relation = new LinkedHashMap<>();
                relation.put("contractCode", item.path("contractCode").asText(null));
                relation.put("billCode", item.path("billCode").asText(null));
                relation.put("companyCode", item.path("companyCode").asText(null));
                relation.put("bindType", item.path("bindType").asInt());
                relation.put("status", item.path("status").asInt());
                relation.put("ctime", DateTimeUtil.format(item.path("ctime").asLong()));
                relation.put("mtime", DateTimeUtil.format(item.path("mtime").asLong()));
                result.add(relation);
            }
            return result;
        } catch (Exception e) {
            log.warn("[ContractQuotationRelationGateway] 解析响应失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
