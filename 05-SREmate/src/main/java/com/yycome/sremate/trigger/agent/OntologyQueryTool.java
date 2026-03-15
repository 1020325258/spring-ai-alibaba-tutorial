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
        【本体论智能查询】根据起始实体和值，自动查询关联数据。

        使用场景：
        - 订单号查询合同及关联数据：entity=Order, value=订单号
        - 合同号查询关联数据：entity=Contract, value=合同号
        - 订单号查询报价单及子单：entity=BudgetBill, value=订单号

        参数：
        - entity: 起始实体类型（Order/Contract/BudgetBill）
        - value: 起始值（订单号或合同号）
        - queryScope: 查询范围（可选）
          - "default": 使用实体默认深度（推荐）
          - 目标实体名（如 ContractNode/ContractField/ContractForm/ContractConfig）: 沿关系路径查询

        示例：
        - "825123110000002753下的合同数据" → entity=Order, value=825123110000002753, queryScope=default
        - "C1767150648920281的版式" → entity=Contract, value=C1767150648920281, queryScope=ContractForm
        - "C1767150648920281的配置表" → entity=Contract, value=C1767150648920281, queryScope=ContractConfig
        - "826031111000001859的报价单" → entity=BudgetBill, value=826031111000001859
        """)
    @DataQueryTool
    public String ontologyQuery(String entity, String value, String queryScope) {
        return ToolExecutionTemplate.execute("ontologyQuery", () -> {
            log.info("[OntologyQueryTool] 查询: entity={}, value={}, scope={}", entity, value, queryScope);

            // 映射 scope 简写到目标实体名
            String resolvedScope = resolveScope(queryScope);

            Map<String, Object> result = queryEngine.query(entity, value, resolvedScope);

            if (result == null) {
                return ToolResult.notFound(entity, value);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 解析 queryScope，支持简写和实体名
     */
    private String resolveScope(String queryScope) {
        if (queryScope == null || "default".equals(queryScope) || "list".equals(queryScope)) {
            return queryScope;
        }
        return SCOPE_ALIAS.getOrDefault(queryScope, queryScope);
    }
}
