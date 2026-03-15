package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.EntityDataGateway;
import com.yycome.sremate.domain.ontology.engine.EntityGatewayRegistry;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 本体论驱动的统一查询工具
 * LLM 只需调用此工具，引擎自动分析依赖并并行执行查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryTool {

    private final EntityRegistry entityRegistry;
    private final EntityGatewayRegistry gatewayRegistry;
    private final ObjectMapper objectMapper;

    private final java.util.concurrent.ExecutorService dbQueryExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(10);

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
          - "list": 仅查询列表，不查关联
          - "nodes": 仅查节点关系
          - "fields": 仅查字段关系
          - "signed_objects": 仅查签约单据关系
          - "budget_bills": 仅查报价单关系
          - "form": 仅查版式数据
          - "config": 仅查配置表数据

        示例：
        - "825123110000002753下的合同数据" → entity=Order, value=825123110000002753, queryScope=default
        - "C1767150648920281的版式" → entity=Contract, value=C1767150648920281, queryScope=form
        - "C1767150648920281的配置表" → entity=Contract, value=C1767150648920281, queryScope=config
        - "826031111000001859的报价单" → entity=BudgetBill, value=826031111000001859
        """)
    @DataQueryTool
    public String ontologyQuery(String entity, String value, String queryScope) {
        return ToolExecutionTemplate.execute("ontologyQuery", () -> {
            log.info("[OntologyQueryTool] 查询: entity={}, value={}, scope={}", entity, value, queryScope);

            // 解析查询范围
            List<String> targetRelations = parseQueryScope(entity, queryScope);

            // 获取实体默认深度
            int maxDepth = getEntityDefaultDepth(entity);

            // 执行查询
            Map<String, Object> result = executeQuery(entity, value, targetRelations, maxDepth);

            if (result == null || result.isEmpty()) {
                return ToolResult.notFound(entity, value);
            }

            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * 解析查询范围
     */
    private List<String> parseQueryScope(String entity, String queryScope) {
        if (queryScope == null || "default".equals(queryScope)) {
            return null; // null 表示查询所有关联
        }

        return switch (queryScope) {
            case "list" -> Collections.emptyList(); // 不查关联
            case "nodes" -> List.of("has_nodes");
            case "fields" -> List.of("has_fields");
            case "signed_objects" -> List.of("has_signed_objects");
            case "budget_bills" -> List.of("has_budget_bills");
            case "form" -> List.of("has_form");
            case "config" -> List.of("has_config");
            default -> null;
        };
    }

    /**
     * 获取实体默认查询深度
     */
    private int getEntityDefaultDepth(String entityName) {
        return entityRegistry.getOntology().getEntities().stream()
            .filter(e -> e.getName().equals(entityName))
            .findFirst()
            .map(e -> e.getDefaultDepth())
            .orElse(2);
    }

    /**
     * 执行查询（核心逻辑）
     */
    private Map<String, Object> executeQuery(String startEntity, Object startValue,
                                              List<String> targetRelations, int maxDepth) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", startEntity);
        result.put("queryValue", startValue);

        // 特殊处理：Order -> Contract/BudgetBill 查询
        if ("Order".equals(startEntity)) {
            return executeOrderQuery(startValue, targetRelations, maxDepth, result);
        }

        // 特殊处理：Contract 查询
        if ("Contract".equals(startEntity)) {
            return executeContractQuery(startValue, targetRelations, maxDepth, result);
        }

        // 特殊处理：BudgetBill 查询
        if ("BudgetBill".equals(startEntity)) {
            return executeBudgetBillQuery(startValue, result);
        }

        return result;
    }

    /**
     * 执行订单查询（Order -> Contract/BudgetBill -> 关联数据）
     */
    private Map<String, Object> executeOrderQuery(Object orderId, List<String> targetRelations,
                                                   int maxDepth, Map<String, Object> result) {
        // 判断是否需要查询报价单
        boolean queryBudgetBills = targetRelations == null || targetRelations.contains("has_budget_bills");
        boolean queryContracts = targetRelations == null ||
                (!targetRelations.contains("has_budget_bills") && targetRelations.isEmpty()) ||
                targetRelations.stream().anyMatch(r -> !r.equals("has_budget_bills"));

        // 并行查询报价单和合同
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 查询报价单
        if (queryBudgetBills) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway budgetBillGateway = gatewayRegistry.getGateway("BudgetBill");
                List<Map<String, Object>> budgetBills = budgetBillGateway.queryByField("projectOrderId", orderId);
                result.put("budgetBills", budgetBills);
            }, dbQueryExecutor));
        }

        // 查询合同
        if (queryContracts) {
            // Step 1: 查询订单下的所有合同
            EntityDataGateway orderGateway = gatewayRegistry.getGateway("Order");
            List<Map<String, Object>> contracts = orderGateway.queryByField("projectOrderId", orderId);

            if (contracts.isEmpty() && !queryBudgetBills) {
                // 等待报价单查询完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                return null;
            }

            if (!contracts.isEmpty()) {
                result.put("contracts", contracts);

                // 如果只要列表或 maxDepth <= 1，不查关联
                if (targetRelations != null && targetRelations.isEmpty() || maxDepth <= 1) {
                    // 继续等待报价单查询完成
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    return result;
                }

                // Step 2: 并行查询每个合同的关联数据
                List<String> contractCodes = contracts.stream()
                    .map(c -> (String) c.get("contractCode"))
                    .toList();

                // 过滤掉 has_budget_bills，只查合同相关关联
                List<String> contractRelations = targetRelations == null ? null :
                    targetRelations.stream()
                        .filter(r -> !r.equals("has_budget_bills"))
                        .toList();

                // 并行查询所有合同的关联数据
                List<CompletableFuture<Map<String, Object>>> contractFutures = contractCodes.stream()
                    .map(code -> CompletableFuture.supplyAsync(
                        () -> queryContractWithRelations(code, contractRelations),
                        dbQueryExecutor
                    ))
                    .toList();

                CompletableFuture.allOf(contractFutures.toArray(new CompletableFuture[0])).join();

                // 组装结果
                List<Map<String, Object>> contractsWithRelations = new ArrayList<>();
                for (int i = 0; i < contractFutures.size(); i++) {
                    Map<String, Object> contract = new LinkedHashMap<>(contracts.get(i));
                    Map<String, Object> relations = contractFutures.get(i).join();
                    contract.putAll(relations);
                    contractsWithRelations.add(contract);
                }

                result.put("contracts", contractsWithRelations);
            }
        }

        // 等待所有查询完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return result;
    }

    /**
     * 执行报价单查询（BudgetBill + SubOrders）
     */
    private Map<String, Object> executeBudgetBillQuery(Object projectOrderId, Map<String, Object> result) {
        EntityDataGateway budgetBillGateway = gatewayRegistry.getGateway("BudgetBill");
        List<Map<String, Object>> budgetBills = budgetBillGateway.queryByField("projectOrderId", projectOrderId);

        if (budgetBills.isEmpty()) {
            return null;
        }

        result.put("budgetBills", budgetBills);
        return result;
    }

    /**
     * 执行合同查询（Contract -> 关联数据）
     */
    private Map<String, Object> executeContractQuery(Object contractCode, List<String> targetRelations,
                                                      int maxDepth, Map<String, Object> result) {
        // 查询合同基本信息
        EntityDataGateway contractGateway = gatewayRegistry.getGateway("Contract");
        List<Map<String, Object>> baseList = contractGateway.queryByField("contractCode", contractCode);

        if (baseList.isEmpty()) {
            return null;
        }

        result.putAll(baseList.get(0));

        // 如果只要基本信息或 maxDepth <= 0，直接返回
        if (targetRelations != null && targetRelations.isEmpty() || maxDepth <= 0) {
            return result;
        }

        // 并行查询关联数据
        Map<String, Object> relations = queryContractWithRelations((String) contractCode, targetRelations);
        result.putAll(relations);

        return result;
    }

    /**
     * 查询单个合同的关联数据（并行）
     */
    private Map<String, Object> queryContractWithRelations(String contractCode, List<String> targetRelations) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 确定要查询的关系
        List<String> relationsToQuery = targetRelations != null ? targetRelations :
            List.of("has_nodes", "has_fields", "has_signed_objects");

        // 并行查询
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (relationsToQuery.contains("has_nodes") || relationsToQuery.isEmpty()) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway gateway = gatewayRegistry.getGateway("ContractNode");
                List<Map<String, Object>> nodes = gateway.queryByField("contractCode", contractCode);
                result.put("nodes", nodes);
            }, dbQueryExecutor));
        }

        if (relationsToQuery.contains("has_fields") || relationsToQuery.isEmpty()) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway gateway = gatewayRegistry.getGateway("ContractField");
                List<Map<String, Object>> fields = gateway.queryByField("contractCode", contractCode);
                result.put("fields", fields.isEmpty() ? null : fields.get(0));
            }, dbQueryExecutor));
        }

        if (relationsToQuery.contains("has_signed_objects") || relationsToQuery.isEmpty()) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway gateway = gatewayRegistry.getGateway("ContractQuotationRelation");
                List<Map<String, Object>> signedObjects = gateway.queryByField("contractCode", contractCode);
                result.put("signedObjects", signedObjects);
            }, dbQueryExecutor));
        }

        if (relationsToQuery.contains("has_form")) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway gateway = gatewayRegistry.getGateway("ContractForm");
                List<Map<String, Object>> formData = gateway.queryByField("contractCode", contractCode);
                if (!formData.isEmpty()) {
                    result.put("form", formData.get(0));
                }
            }, dbQueryExecutor));
        }

        if (relationsToQuery.contains("has_config")) {
            futures.add(CompletableFuture.runAsync(() -> {
                EntityDataGateway gateway = gatewayRegistry.getGateway("ContractConfig");
                List<Map<String, Object>> configData = gateway.queryByField("contractCode", contractCode);
                if (!configData.isEmpty()) {
                    result.put("config", configData.get(0));
                }
            }, dbQueryExecutor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return result;
    }
}
