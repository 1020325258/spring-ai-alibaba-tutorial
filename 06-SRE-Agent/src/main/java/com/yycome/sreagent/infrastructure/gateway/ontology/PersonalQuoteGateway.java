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
 * PersonalQuote（个性化报价）实体的数据网关
 * <p>
 * 通过签约单据(ContractQuotationRelation)查询，根据bindType映射参数：
 * - bindType=1 → billCodeList（报价单）
 * - bindType=2 → changeOrderId（变更单）
 * - bindType=3 → subOrderNoList（S单号）
 * <p>
 * 返回属性遵循 domain-ontology.yaml 定义：
 * - projectOrderId: 订单号
 * - billCode: 报价单号
 * - personalContractPrice: 个性化报价金额
 * - organizationCode: 组织编码
 * - organizationName: 组织名称
 * - createTime: 创建时间
 * - quoteFileUrl: 报价文件URL
 * - quotePrevUrl: 报价预览URL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalQuoteGateway implements EntityDataGateway {

    private final HttpEndpointClient httpEndpointClient;
    private final EntityGatewayRegistry registry;
    private final ObjectMapper objectMapper;

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
        Map<String, Object> hintRecord = JsonMappingUtils.newOrderedMap();
        hintRecord.put("_hint", "PersonalQuote 需要通过签约单据(ContractQuotationRelation)查询，" +
                "请使用三跳路径：订单 → 合同 → 签约单据 → 个性化报价");
        return List.of(hintRecord);
    }

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
            Map<String, Object> hintRecord = JsonMappingUtils.newOrderedMap();
            hintRecord.put("_hint", "请提供至少一种单据号：S单号（如 S15260312120004471）、" +
                    "报价单号（如 GBILL260312104241050001）或变更单号，以便查询个性化报价数据。");
            hintRecord.put("projectOrderId", projectOrderId);
            return List.of(hintRecord);
        }

        return doQuery(projectOrderId, extraParams);
    }

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

            return parsePersonalQuotes(resultJson, projectOrderId);
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 查询个性化报价失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 解析个性化报价数据
     * <p>
     * 按 YAML 定义的属性组装返回
     */
    private List<Map<String, Object>> parsePersonalQuotes(String json, String projectOrderId) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);

            int code = root.path("code").asInt(-1);
            if (code != 2000) {
                log.warn("[PersonalQuoteGateway] 接口返回非成功状态: code={}", code);
                return result;
            }

            JsonNode dataList = root.path("data").path("personalContractDataList");
            if (!dataList.isArray()) {
                log.debug("[PersonalQuoteGateway] 无个性化报价数据");
                return result;
            }

            for (JsonNode quoteData : dataList) {
                Map<String, Object> record = JsonMappingUtils.newOrderedMap();
                record.put("projectOrderId", projectOrderId);
                record.put("billCode", JsonMappingUtils.getText(quoteData, "billCode"));
                record.put("personalContractPrice", JsonMappingUtils.getText(quoteData, "personalContractPrice"));
                record.put("organizationCode", JsonMappingUtils.getText(quoteData, "organizationCode"));
                record.put("organizationName", JsonMappingUtils.getText(quoteData, "organizationName"));
                record.put("createTime", JsonMappingUtils.getText(quoteData, "createTime"));

                JsonNode quoteInfo = quoteData.path("quoteInfo");
                if (!quoteInfo.isMissingNode()) {
                    record.put("quoteFileUrl", JsonMappingUtils.getText(quoteInfo, "fileUrl"));
                    record.put("quotePrevUrl", JsonMappingUtils.getText(quoteInfo, "prevUrl"));
                }

                result.add(record);
            }

            log.debug("[PersonalQuoteGateway] 解析成功，返回 {} 条报价数据", result.size());
        } catch (Exception e) {
            log.warn("[PersonalQuoteGateway] 解析 JSON 失败: {}", e.getMessage());
        }

        return result;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
