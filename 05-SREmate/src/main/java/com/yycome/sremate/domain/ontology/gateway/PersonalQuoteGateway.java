package com.yycome.sremate.domain.ontology.gateway;

import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.infrastructure.client.HttpEndpointClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PersonalQuote（个性化报价）实体的数据网关
 * 需要额外参数：subOrderNoList、billCodeList、changeOrderId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalQuoteGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final EntityGatewayRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getEntityName() {
        return "PersonalQuote";
    }

    @Override
    public List<Map<String, Object>> queryByField(String fieldName, Object value) {
        // PersonalQuote 必须提供额外参数，直接调用带参数的方法会返回提示信息
        log.warn("[PersonalQuoteGateway] queryByField 被调用，但需要额外参数");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> queryWithExtraParams(String fieldName, Object value, Map<String, String> extraParams) {
        log.debug("[PersonalQuoteGateway] queryWithExtraParams: {} = {}, extraParams={}", fieldName, value, extraParams);

        if (!"projectOrderId".equals(fieldName)) {
            throw new IllegalArgumentException("PersonalQuote 不支持字段: " + fieldName);
        }

        String projectOrderId = String.valueOf(value);

        // 检查额外参数是否至少有一个
        String subOrderNoList = extraParams != null ? extraParams.getOrDefault("subOrderNoList", "") : "";
        String billCodeList = extraParams != null ? extraParams.getOrDefault("billCodeList", "") : "";
        String changeOrderId = extraParams != null ? extraParams.getOrDefault("changeOrderId", "") : "";

        boolean allEmpty = isBlank(subOrderNoList) && isBlank(billCodeList) && isBlank(changeOrderId);
        if (allEmpty) {
            // 返回提示信息而非空列表，让调用方知道需要额外参数
            Map<String, Object> hintRecord = new LinkedHashMap<>();
            hintRecord.put("_hint", "请提供至少一种单据号：S单号（如 S15260312120004471）、" +
                    "报价单号（如 GBILL260312104241050001）或变更单号，以便查询个性化报价数据。");
            hintRecord.put("projectOrderId", projectOrderId);
            return List.of(hintRecord);
        }

        try {
            Map<String, String> params = new HashMap<>();
            params.put("projectOrderId", projectOrderId);
            params.put("subOrderNoList", subOrderNoList != null ? subOrderNoList : "");
            params.put("billCodeList", billCodeList != null ? billCodeList : "");
            params.put("changeOrderId", changeOrderId != null ? changeOrderId : "");

            String resultJson = httpEndpointClient.callPredefinedEndpointRaw("contract-personal-data", params);

            if (resultJson != null && resultJson.contains("\"error\"")) {
                log.warn("[PersonalQuoteGateway] 接口返回错误: {}", resultJson);
                return Collections.emptyList();
            }

            return parsePersonalQuote(resultJson, projectOrderId, subOrderNoList, billCodeList, changeOrderId);
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 查询个性化报价失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> parsePersonalQuote(String json, String projectOrderId,
                                                          String subOrderNoList, String billCodeList,
                                                          String changeOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("projectOrderId", projectOrderId);
        record.put("subOrderNoList", subOrderNoList);
        record.put("billCodeList", billCodeList);
        record.put("changeOrderId", changeOrderId);
        record.put("_rawData", json);
        result.add(record);
        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
