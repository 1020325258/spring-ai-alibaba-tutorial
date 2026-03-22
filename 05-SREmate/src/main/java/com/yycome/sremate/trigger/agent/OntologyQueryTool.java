package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.OntologyQueryEngine;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 本体论驱动的统一查询工具（薄层）
 * 仅负责参数解析和结果格式化，核心逻辑委托给 OntologyQueryEngine
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryTool {

    private final OntologyQueryEngine queryEngine;
    private final ObjectMapper objectMapper;
    private final EntityRegistry entityRegistry;

    @Tool(description = """
        【本体论智能查询】根据起始实体和值，查询实体数据及关联数据。

        参数：
        - entity: 起始实体类型（根据用户意图和上下文判断）
          - Order: 订单（订单号）
          - Contract: 合同（合同号）
          - ContractInstance: 合同实例（instanceId）
          - BudgetBill: 报价单（订单号）
          - 其他实体见系统提示词中的【可用实体】列表
        - value: 起始值（订单号、合同号、实例ID等，根据上下文判断）
        - queryScope: 目标实体（用户想查询什么数据，就传对应实体名）
          - 不传或 "list": 仅返回起始实体本身，不展开关联
          - 目标实体名: 展开到该实体（如 ContractNode、ContractInstance）
          - 多个目标（逗号分隔）: "ContractNode,ContractQuotationRelation"

        示例：
        - "825123110000002753的合同" → entity=Order, value=825123110000002753, queryScope=Contract
        - "C1767150648920281的节点" → entity=Contract, value=C1767150648920281, queryScope=ContractNode
        - "101835395的实例信息" → entity=ContractInstance, value=101835395
        - "825123110000002753合同的签约单据和节点" → entity=Order, value=825123110000002753, queryScope=ContractNode,ContractQuotationRelation
        """)
    @DataQueryTool
    public String ontologyQuery(String entity, String value, String queryScope) {
        // 校验 entity 合法性
        validateEntity(entity);
        final String finalEntity = entity;

        return ToolExecutionTemplate.execute("ontologyQuery", () -> {
            log.info("[OntologyQueryTool] 查询: entity={}, value={}, scope={}", finalEntity, value, queryScope);

            Map<String, Object> result = queryEngine.query(finalEntity, value, queryScope);

            if (result == null) {
                return ToolResult.notFound(finalEntity, value);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 校验 entity 是否合法（在 EntityRegistry 中存在）
     */
    private void validateEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("entity 参数不能为空");
        }
        if (!entityRegistry.entityExists(entity)) {
            throw new IllegalArgumentException("未知实体: " + entity +
                "，可用实体: " + entityRegistry.getOntology().getEntities().stream()
                    .map(e -> e.getName()).toList());
        }
    }

    @Tool(description = """
        【个性化报价查询】用户提到"个性化报价"时使用。

        触发条件：包含关键词"个性化报价"

        参数：
        - projectOrderId：纯数字订单号（必填）
        - subOrderNoList：S单号列表，逗号分隔（可选，如 S15260312120004471）
        - billCodeList：报价单号列表，逗号分隔（可选，如 GBILL260312104241050001）
        - changeOrderId：变更单号（可选，格式与订单号类似）

        约束：subOrderNoList、billCodeList、changeOrderId 至少填一个

        示例：
        - "826031210000003581下S15260312120004471的个性化报价"
          → projectOrderId=826031210000003581, subOrderNoList=S15260312120004471
        - "826031210000003581的GBILL260312104241050001个性化报价"
          → projectOrderId=826031210000003581, billCodeList=GBILL260312104241050001""")
    @DataQueryTool
    public String queryPersonalQuote(String projectOrderId,
                                     String subOrderNoList,
                                     String changeOrderId,
                                     String billCodeList) {
        return ToolExecutionTemplate.execute("queryPersonalQuote", () -> {
            log.info("[OntologyQueryTool] 个性化报价查询: projectOrderId={}, subOrderNoList={}, billCodeList={}, changeOrderId={}",
                    projectOrderId, subOrderNoList, billCodeList, changeOrderId);

            Map<String, String> extraParams = new HashMap<>();
            extraParams.put("subOrderNoList", subOrderNoList != null ? subOrderNoList : "");
            extraParams.put("billCodeList", billCodeList != null ? billCodeList : "");
            extraParams.put("changeOrderId", changeOrderId != null ? changeOrderId : "");

            Map<String, Object> result = queryEngine.query("Order", projectOrderId, "PersonalQuote", extraParams);

            if (result == null) {
                return ToolResult.notFound("PersonalQuote", projectOrderId);
            }

            return objectMapper.writeValueAsString(result);
        });
    }
}
