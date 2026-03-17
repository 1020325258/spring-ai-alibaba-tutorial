package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.OntologyQueryEngine;
import com.yycome.sremate.domain.ontology.model.QueryScope;
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

    @Tool(description = """
        【本体论智能查询】根据起始实体和值，查询实体数据及关联数据。

        参数：
        - entity: 起始实体类型（根据用户提供的编号格式判断）
          - 订单号（纯数字）：Order
          - 合同号（C开头）：Contract
        - value: 起始值（订单号或合同号）
        - queryScope: 目标实体（用户想查询什么数据，就传对应实体名）
          - 不传或 "list": 仅返回起始实体本身，不展开关联
          - "Contract": 展开到合同数据
          - "ContractNode": 展开到节点数据
          - "ContractQuotationRelation": 展开到签约单据
          - "ContractField": 展开到字段数据
          - "ContractForm": 展开到版式数据
          - "ContractConfig": 展开到配置表数据
          - 多个目标（逗号分隔）: "ContractNode,ContractQuotationRelation"

        示例：
        - "825123110000002753下的合同" → entity=Order, value=825123110000002753, queryScope=Contract
        - "C1767150648920281的节点" → entity=Contract, value=C1767150648920281, queryScope=ContractNode
        - "825123110000002753合同的签约单据和节点" → entity=Order, value=825123110000002753, queryScope=ContractNode,ContractQuotationRelation
        - "826031111000001859的报价单" → entity=Order, value=826031111000001859, queryScope=BudgetBill
        """)
    @DataQueryTool
    public String ontologyQuery(String entity, String value, String queryScope) {
        return ToolExecutionTemplate.execute("ontologyQuery", () -> {
            log.info("[OntologyQueryTool] 查询: entity={}, value={}, scope={}", entity, value, queryScope);

            Map<String, Object> result = queryEngine.query(entity, value, queryScope);

            if (result == null) {
                return ToolResult.notFound(entity, value);
            }

            return objectMapper.writeValueAsString(result);
        });
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
