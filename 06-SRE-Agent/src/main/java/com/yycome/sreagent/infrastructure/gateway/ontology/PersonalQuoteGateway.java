package com.yycome.sreagent.infrastructure.gateway.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sreagent.domain.ontology.engine.EntityDataGateway;
import com.yycome.sreagent.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sreagent.domain.ontology.engine.EntitySchemaMapper;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import com.yycome.sreagent.infrastructure.client.HttpEndpointClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * PersonalQuote（个性化报价）实体的数据网关
 *
 * 通过签约单据(ContractQuotationRelation)查询，根据bindType映射参数：
 * - bindType=1 → billCodeList（报价单）
 * - bindType=2 → changeOrderId（变更单）
 * - bindType=3 → subOrderNoList（S单号）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalQuoteGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final EntityGatewayRegistry registry;
    private final ObjectMapper objectMapper;
    private final EntitySchemaMapper schemaMapper;
    private final EntityRegistry entityRegistry;

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
        log.warn("[PersonalQuoteGateway] queryByField 被调用，但需要父记录上下文或额外参数");
        Map<String, Object> hintRecord = new LinkedHashMap<>();
        hintRecord.put("_hint", "PersonalQuote 需要通过签约单据(ContractQuotationRelation)查询，" +
                "请使用三跳路径：订单 → 合同 → 签约单据 → 个性化报价");
        return List.of(hintRecord);
    }

    /**
     * 从签约单据(ContractQuotationRelation)查询个性化报价
     *
     * @param fieldName    目标字段（忽略，实际从父记录提取参数）
     * @param value        billCode 值（来自签约单据）
     * @param parentRecord 父记录（ContractQuotationRelation），包含 contractCode、billCode、bindType
     */
    @Override
    public List<Map<String, Object>> queryByFieldWithContext(String fieldName, Object value, Map<String, Object> parentRecord) {
        log.debug("[PersonalQuoteGateway] queryByFieldWithContext: fieldName={}, value={}, parentRecord={}",
                fieldName, value, parentRecord != null ? parentRecord.keySet() : "null");

        if (parentRecord == null) {
            log.warn("[PersonalQuoteGateway] 缺少父记录上下文，无法查询个性化报价");
            return Collections.emptyList();
        }

        String billCode = String.valueOf(parentRecord.getOrDefault("billCode", ""));
        String bindType = String.valueOf(parentRecord.getOrDefault("bindType", ""));
        String contractCode = String.valueOf(parentRecord.getOrDefault("contractCode", ""));

        if (isBlank(billCode) || "null".equals(billCode)) {
            log.warn("[PersonalQuoteGateway] 父记录缺少 billCode，无法查询");
            return Collections.emptyList();
        }

        String projectOrderId = resolveProjectOrderId(contractCode);
        if (isBlank(projectOrderId)) {
            log.warn("[PersonalQuoteGateway] 无法从合同 {} 获取 projectOrderId", contractCode);
            return Collections.emptyList();
        }

        Map<String, String> extraParams = mapBindTypeToParams(bindType, billCode);

        if (extraParams.isEmpty()) {
            log.warn("[PersonalQuoteGateway] 无效的 bindType={}，contractCode={}，跳过查询", bindType, contractCode);
            return Collections.emptyList();
        }

        return doQuery(projectOrderId, extraParams);
    }

    /**
     * 独立查询入口（向后兼容，用于直接查询场景）
     */
    @Override
    public List<Map<String, Object>> queryWithExtraParams(String fieldName, Object value, Map<String, String> extraParams) {
        log.debug("[PersonalQuoteGateway] queryWithExtraParams: {} = {}, extraParams={}", fieldName, value, extraParams);

        if (!"projectOrderId".equals(fieldName)) {
            throw new IllegalArgumentException("PersonalQuote 不支持字段: " + fieldName);
        }

        String projectOrderId = String.valueOf(value);

        String subOrderNoList = extraParams != null ? extraParams.getOrDefault("subOrderNoList", "") : "";
        String billCodeList = extraParams != null ? extraParams.getOrDefault("billCodeList", "") : "";
        String changeOrderId = extraParams != null ? extraParams.getOrDefault("changeOrderId", "") : "";

        boolean allEmpty = isBlank(subOrderNoList) && isBlank(billCodeList) && isBlank(changeOrderId);
        if (allEmpty) {
            Map<String, Object> hintRecord = new LinkedHashMap<>();
            hintRecord.put("_hint", "请提供至少一种单据号：S单号（如 S15260312120004471）、" +
                    "报价单号（如 GBILL260312104241050001）或变更单号，以便查询个性化报价数据。");
            hintRecord.put("projectOrderId", projectOrderId);
            return List.of(hintRecord);
        }

        return doQuery(projectOrderId, extraParams);
    }

    /**
     * 根据 bindType 映射到查询参数
     */
    private Map<String, String> mapBindTypeToParams(String bindType, String billCode) {
        Map<String, String> params = new HashMap<>();

        switch (bindType) {
            case "1":
                params.put("billCodeList", billCode);
                break;
            case "2":
                params.put("changeOrderId", billCode);
                break;
            case "3":
                params.put("subOrderNoList", billCode);
                break;
            default:
                log.warn("[PersonalQuoteGateway] 无效的 bindType={}，应为 1/2/3", bindType);
                return Collections.emptyMap();
        }

        return params;
    }

    /**
     * 通过 contractCode 查询 projectOrderId
     */
    private String resolveProjectOrderId(String contractCode) {
        if (isBlank(contractCode) || "null".equals(contractCode)) {
            return null;
        }

        try {
            String json = httpEndpointClient.callPredefinedEndpointRaw("sre-contract",
                    Map.of("contractCode", contractCode));
            if (json == null) {
                log.warn("[PersonalQuoteGateway] 查询合同 {} 失败", contractCode);
                return null;
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            if (data.isObject() && data.has("projectOrderId")) {
                return data.get("projectOrderId").asText(null);
            }
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 查询合同 {} 失败: {}", contractCode, e.getMessage());
        }

        return null;
    }

    /**
     * 执行 HTTP 查询
     */
    private List<Map<String, Object>> doQuery(String projectOrderId, Map<String, String> extraParams) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("projectOrderId", projectOrderId);
            params.put("subOrderNoList", extraParams.getOrDefault("subOrderNoList", ""));
            params.put("billCodeList", extraParams.getOrDefault("billCodeList", ""));
            params.put("changeOrderId", extraParams.getOrDefault("changeOrderId", ""));

            String resultJson = httpEndpointClient.callPredefinedEndpointRaw("contract-personal-data", params);

            if (resultJson != null && resultJson.contains("\"error\"")) {
                log.warn("[PersonalQuoteGateway] 接口返回错误: {}", resultJson);
                return Collections.emptyList();
            }

            // YAML 驱动的新解析方法
            List<Map<String, Object>> newResult = parsePersonalQuoteNew(resultJson, projectOrderId);

            // 一致性校验
            consistencyCheck(newResult, resultJson, projectOrderId);

            return newResult;
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 查询个性化报价失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * YAML 驱动的新解析方法
     */
    private List<Map<String, Object>> parsePersonalQuoteNew(String rawJson, String projectOrderId) {
        OntologyEntity entity = entityRegistry.getEntity("PersonalQuote");
        if (entity == null || entity.getAttributes() == null) {
            log.warn("[PersonalQuoteGateway] 未找到实体定义，使用旧方法");
            return parsePersonalQuoteOld(rawJson, projectOrderId);
        }

        boolean hasSourceConfig = entity.getAttributes().stream()
                .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

        if (!hasSourceConfig) {
            return parsePersonalQuoteOld(rawJson, projectOrderId);
        }

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("projectOrderId", projectOrderId);
        return schemaMapper.map(entity, rawJson, queryParams);
    }

    /**
     * 一致性校验
     */
    private void consistencyCheck(List<Map<String, Object>> newResult, String rawJson, String projectOrderId) {
        OntologyEntity entity = entityRegistry.getEntity("PersonalQuote");
        if (entity != null && entity.getAttributes() != null) {
            boolean hasSourceConfig = entity.getAttributes().stream()
                    .anyMatch(attr -> attr.getSource() != null && !attr.getSource().isEmpty());

            if (hasSourceConfig) {
                List<Map<String, Object>> oldResult = parsePersonalQuoteOld(rawJson, projectOrderId);
                if (!equals(newResult, oldResult)) {
                    log.error("[PersonalQuoteGateway] 新旧方法输出一致性校验失败! newResult={}, oldResult={}",
                            newResult, oldResult);
                }
            }
        }
    }

    /**
     * 旧解析方法（保留用于一致性校验）
     * @deprecated 使用 parsePersonalQuoteNew 代替
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePersonalQuoteOld(String json, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            Map<String, Object> response = objectMapper.readValue(json, Map.class);

            Integer code = (Integer) response.get("code");
            if (code == null || code != 2000) {
                log.warn("[PersonalQuoteGateway] 接口返回非成功状态: code={}, message={}",
                        code, response.get("message"));
                return result;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) {
                log.warn("[PersonalQuoteGateway] 响应缺少 data 字段");
                return result;
            }

            List<Map<String, Object>> personalContractDataList =
                    (List<Map<String, Object>>) data.get("personalContractDataList");

            if (personalContractDataList == null || personalContractDataList.isEmpty()) {
                log.debug("[PersonalQuoteGateway] 无个性化报价数据");
                return result;
            }

            for (Map<String, Object> quoteData : personalContractDataList) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("projectOrderId", projectOrderId);
                record.put("billCode", quoteData.get("billCode"));
                record.put("personalContractPrice", quoteData.get("personalContractPrice"));
                record.put("organizationCode", quoteData.get("organizationCode"));
                record.put("organizationName", quoteData.get("organizationName"));
                record.put("createTime", quoteData.get("createTime"));

                Map<String, Object> quoteInfo = (Map<String, Object>) quoteData.get("quoteInfo");
                if (quoteInfo != null) {
                    record.put("quoteFileUrl", quoteInfo.get("fileUrl"));
                    record.put("quotePrevUrl", quoteInfo.get("prevUrl"));
                }

                result.add(record);
            }

            log.debug("[PersonalQuoteGateway] 解析成功，返回 {} 条报价数据", result.size());
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 解析 JSON 失败: {}", e.getMessage());
            Map<String, Object> fallbackRecord = new LinkedHashMap<>();
            fallbackRecord.put("projectOrderId", projectOrderId);
            fallbackRecord.put("_rawData", json);
            fallbackRecord.put("_parseError", e.getMessage());
            result.add(fallbackRecord);
        }

        return result;
    }

    /**
     * 比较两个结果列表是否相等
     */
    private boolean equals(List<Map<String, Object>> list1, List<Map<String, Object>> list2) {
        if (list1 == null && list2 == null) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;

        for (int i = 0; i < list1.size(); i++) {
            Map<String, Object> map1 = list1.get(i);
            Map<String, Object> map2 = list2.get(i);

            if (map1.size() != map2.size()) return false;

            for (String key : map1.keySet()) {
                Object val1 = map1.get(key);
                Object val2 = map2.get(key);

                if (val1 == null && val2 == null) continue;
                if (val1 == null || val2 == null) return false;

                if (val1 instanceof Number && val2 instanceof Number) {
                    if (((Number) val1).doubleValue() != ((Number) val2).doubleValue()) {
                        return false;
                    }
                } else if (!val1.equals(val2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
