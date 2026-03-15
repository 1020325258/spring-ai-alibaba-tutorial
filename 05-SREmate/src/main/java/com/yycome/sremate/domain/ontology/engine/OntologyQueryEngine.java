package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.LookupStrategy;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本体论查询执行引擎
 * 完全由 YAML 关系图驱动，支持 default 展开和 scoped 路径查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryEngine {

    private final EntityRegistry entityRegistry;
    private final EntityGatewayRegistry gatewayRegistry;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 对外唯一入口
     *
     * @param entityName  起始实体名（Order / Contract / BudgetBill）
     * @param value       标识值（订单号 / 合同号等）
     * @param queryScope  目标实体名（ContractNode 等），null/default 按 defaultDepth 展开，list 仅返回列表
     * @return 层级结构的查询结果，起始实体无数据时返回 null
     */
    public Map<String, Object> query(String entityName, String value, String queryScope) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        if (records.isEmpty()) return null;

        // "list" 表示仅返回列表，不展开关联
        if (!"list".equals(queryScope)) {
            if (queryScope == null || "default".equals(queryScope)) {
                expandDefault(entityName, records, entity.getDefaultDepth());
            } else {
                List<OntologyRelation> path = entityRegistry.findRelationPath(entityName, queryScope);
                if (path == null) {
                    throw new IllegalArgumentException(
                        "找不到路径: " + entityName + " -> " + queryScope +
                        "，请检查 domain-ontology.yaml 中的关系定义");
                }
                attachPathResults(records, path, 0);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", entityName);
        result.put("queryValue", value);
        result.put("records", records);
        return result;
    }

    /**
     * 按 defaultDepth 递归展开所有出边（同层并行）
     */
    private void expandDefault(String entityName, List<Map<String, Object>> records, int depth) {
        if (depth <= 0) return;

        List<OntologyRelation> outgoing = entityRegistry.getOutgoingRelations(entityName);
        if (outgoing.isEmpty()) return;

        // 对每条记录，并行展开所有出边
        List<CompletableFuture<Void>> futures = records.stream()
            .map(record -> CompletableFuture.runAsync(() -> {
                for (OntologyRelation rel : outgoing) {
                    Object childValue = record.get(rel.getVia().get("source_field"));
                    if (childValue == null) continue;
                    List<Map<String, Object>> children =
                        gatewayRegistry.getGateway(rel.getTo())
                            .queryByField(rel.getVia().get("target_field"), childValue);
                    expandDefault(rel.getTo(), children, depth - 1);
                    record.put(deriveKey(rel.getLabel()), children);
                }
            }, executor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 沿 path 递归挂载子结果（同层并行）
     */
    private void attachPathResults(List<Map<String, Object>> records,
                                    List<OntologyRelation> path, int hop) {
        if (hop >= path.size()) return;

        OntologyRelation rel = path.get(hop);
        String resultKey = deriveKey(rel.getLabel());

        List<CompletableFuture<Void>> futures = records.stream()
            .map(record -> CompletableFuture.runAsync(() -> {
                Object childValue = record.get(rel.getVia().get("source_field"));
                if (childValue == null) return;
                List<Map<String, Object>> children =
                    gatewayRegistry.getGateway(rel.getTo())
                        .queryByField(rel.getVia().get("target_field"), childValue);
                attachPathResults(children, path, hop + 1);
                record.put(resultKey, children);
            }, executor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 匹配 value 对应的 LookupStrategy
     */
    private LookupStrategy matchStrategy(OntologyEntity entity, String value) {
        if (entity.getLookupStrategies() == null || entity.getLookupStrategies().isEmpty()) {
            throw new IllegalStateException("实体 " + entity.getName() + " 未配置 lookupStrategies");
        }
        return entity.getLookupStrategies().stream()
            .filter(s -> value.matches(s.getPattern()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "无法识别的 value 格式: " + value +
                "，实体 " + entity.getName() + " 支持的格式: " +
                entity.getLookupStrategies().stream()
                    .map(LookupStrategy::getPattern).toList()));
    }

    /**
     * 从 relation label 推导结果 key
     * has_signed_objects -> signedObjects
     * splits_into -> subOrders（特殊处理：to 实体名小驼峰 + s）
     */
    private String deriveKey(String label) {
        String stripped = label.startsWith("has_") ? label.substring(4) : label;
        // snake_case -> camelCase
        String[] parts = stripped.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
