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
     * @param queryScope  目标实体名，支持多种格式：
     *                    - null/"default"/"list": 仅返回实体列表，不展开关联
     *                    - 单个目标: "Contract" 或 "ContractNode" 展开到该目标的路径
     *                    - 多个目标: "ContractNode,ContractQuotationRelation" 展开到多个目标的路径
     * @return 层级结构的查询结果，起始实体无数据时返回 null
     */
    public Map<String, Object> query(String entityName, String value, String queryScope) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        if (records.isEmpty()) return null;

        // null/"default"/"list" 均表示仅返回列表，不展开关联
        // 只有明确指定目标实体时才展开关联
        if (queryScope != null && !"default".equals(queryScope) && !"list".equals(queryScope)) {
            // 支持多目标查询：按逗号分隔
            List<String> targets = Arrays.asList(queryScope.split(","));
            List<List<OntologyRelation>> paths = new ArrayList<>();
            for (String target : targets) {
                List<OntologyRelation> path = entityRegistry.findRelationPath(entityName, target.trim());
                if (path == null) {
                    throw new IllegalArgumentException(
                        "找不到路径: " + entityName + " -> " + target.trim() +
                        "，请检查 domain-ontology.yaml 中的关系定义");
                }
                paths.add(path);
            }
            attachMultiPathResults(records, paths);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", entityName);
        result.put("queryValue", value);
        result.put("records", records);
        return result;
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
     * 多目标路径展开
     * 对每条记录，展开所有目标路径，合并相同层级的查询结果
     */
    private void attachMultiPathResults(List<Map<String, Object>> records,
                                         List<List<OntologyRelation>> paths) {
        // 按层级分组：hop -> 关系列表（去重）
        Map<Integer, Set<OntologyRelation>> hopRelations = new LinkedHashMap<>();
        for (List<OntologyRelation> path : paths) {
            for (int hop = 0; hop < path.size(); hop++) {
                hopRelations.computeIfAbsent(hop, k -> new LinkedHashSet<>()).add(path.get(hop));
            }
        }

        // 逐层展开
        attachLayer(records, hopRelations, 0);
    }

    /**
     * 递归展开某一层的关系
     */
    private void attachLayer(List<Map<String, Object>> records,
                              Map<Integer, Set<OntologyRelation>> hopRelations,
                              int hop) {
        if (!hopRelations.containsKey(hop)) return;

        Set<OntologyRelation> relsAtHop = hopRelations.get(hop);

        // 对每条记录，并行展开当前层的所有关系
        List<CompletableFuture<Void>> futures = records.stream()
            .map(record -> CompletableFuture.runAsync(() -> {
                for (OntologyRelation rel : relsAtHop) {
                    Object childValue = record.get(rel.getVia().get("source_field"));
                    if (childValue == null) continue;

                    String resultKey = deriveKey(rel.getLabel());

                    // 如果已有该 key，跳过（避免重复查询）
                    if (record.containsKey(resultKey)) continue;

                    List<Map<String, Object>> children =
                        gatewayRegistry.getGateway(rel.getTo())
                            .queryByField(rel.getVia().get("target_field"), childValue);

                    // 递归展开下一层
                    attachLayer(children, hopRelations, hop + 1);

                    record.put(resultKey, children);
                }
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
