package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import com.yycome.sreagent.infrastructure.util.JsonMappingUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ContractQuotationRelation 实体的数据网关
 * <p>
 * 通过 HTTP 接口查询签约单据数据
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - contractCode: 合同编号
 * - billCode: 签约单据编号
 * - companyCode: 公司编码
 * - bindType: 绑定类型
 * - status: 状态
 * - ctime: 创建时间
 * - mtime: 修改时间
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

    /**
     * 解析签约单据数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parseQuotations(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return result;
            }

            for (JsonNode item : data) {
                Map<String, Object> relation = JsonMappingUtils.newOrderedMap();
                relation.put("contractCode", JsonMappingUtils.getText(item, "contractCode"));
                relation.put("billCode", JsonMappingUtils.getText(item, "billCode"));
                relation.put("companyCode", JsonMappingUtils.getText(item, "companyCode"));
                relation.put("bindType", JsonMappingUtils.getInt(item, "bindType"));
                relation.put("status", JsonMappingUtils.getInt(item, "status"));
                relation.put("ctime", JsonMappingUtils.formatDateTime(item, "ctime"));
                relation.put("mtime", JsonMappingUtils.formatDateTime(item, "mtime"));
                result.add(relation);
            }
        } catch (Exception e) {
            log.warn("[ContractQuotationRelationGateway] 解析响应失败: {}", e.getMessage());
        }
        return result;
    }
}
