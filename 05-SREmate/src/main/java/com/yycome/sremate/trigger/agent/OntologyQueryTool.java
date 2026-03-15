package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.OntologyQueryEngine;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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

    // scope 简写 -> 目标实体名映射（兼容旧调用方式）
    private static final Map<String, String> SCOPE_ALIAS = Map.of(
        "nodes", "ContractNode",
        "fields", "ContractField",
        "signed_objects", "ContractQuotationRelation",
        "budget_bills", "BudgetBill",
        "form", "ContractForm",
        "config", "ContractConfig"
    );

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

            // 映射 scope 简写到目标实体名（支持多目标）
            String resolvedScope = resolveScope(queryScope);

            Map<String, Object> result = queryEngine.query(entity, value, resolvedScope);

            if (result == null) {
                return ToolResult.notFound(entity, value);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 解析 queryScope，支持简写、实体名和多目标
     */
    private String resolveScope(String queryScope) {
        if (queryScope == null || "default".equals(queryScope) || "list".equals(queryScope)) {
            return queryScope;
        }

        // 支持多目标：按逗号分隔，分别映射
        return Arrays.stream(queryScope.split(","))
                .map(String::trim)
                .map(s -> SCOPE_ALIAS.getOrDefault(s, s))
                .collect(Collectors.joining(","));
    }
}
