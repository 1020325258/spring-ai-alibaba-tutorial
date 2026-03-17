package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.LookupStrategy;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import com.yycome.sremate.domain.ontology.model.QueryScope;
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
     * 对外唯一入口（字符串版本）
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
        QueryScope scope = QueryScope.fromString(queryScope);
        if (scope != null) {
            return query(entityName, value, scope);
        }
        // 未匹配到枚举，可能是自定义实体名或逗号分隔的多目标
        return queryWithScopeString(entityName, value, queryScope);
    }

    /**
     * 对外唯一入口（枚举版本）
     *
     * @param entityName  起始实体名（Order / Contract / BudgetBill）
     * @param value       标识值（订单号 / 合同号等）
     * @param queryScope  查询范围枚举
     * @return 层级结构的查询结果，起始实体无数据时返回 null
     */
    public Map<String, Object> query(String entityName, String value, QueryScope queryScope) {
        if (queryScope == QueryScope.LIST || queryScope == QueryScope.DEFAULT) {
            // 仅返回起始实体
            return queryListOnly(entityName, value);
        }
        String targetEntity = queryScope.getTargetEntity();
        return queryWithScopeString(entityName, value, targetEntity);
    }

    /**
     * 仅查询起始实体，不展开关联
     */
    private Map<String, Object> queryListOnly(String entityName, String value) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        if (records.isEmpty()) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", entityName);
        result.put("queryValue", value);
        result.put("records", records);
        return result;
    }

    /**
     * 使用字符串 scope 进行查询（支持多目标）
     */
    private Map<String, Object> queryWithScopeString(String entityName, String value, String queryScope) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        // 起始节点查询
        log.info("▶ 起始节点 {} | key={} | value={}", entityName, strategy.getField(), value);
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        log.info("  ↳ 返回 {} 条记录, 耗时 {}ms", records.size(), System.currentTimeMillis() - startTime);

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
            // 打印路径规划日志
            logPathPlan(entityName, paths);
            attachMultiPathResults(records, paths);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", entityName);
        result.put("queryValue", value);
        result.put("records", records);
        return result;
    }

    /**
     * 打印路径规划日志
     */
    private void logPathPlan(String startEntity, List<List<OntologyRelation>> paths) {
        StringBuilder sb = new StringBuilder();
        sb.append("路径规划: ").append(startEntity);

        for (List<OntologyRelation> path : paths) {
            sb.append(" → ");
            for (int i = 0; i < path.size(); i++) {
                OntologyRelation rel = path.get(i);
                if (i == 0) sb.append(" -[");
                else sb.append(" -[");
                sb.append(rel.getVia().get("source_field"))
                  .append("→")
                  .append(rel.getVia().get("target_field"))
                  .append("]-> ")
                  .append(rel.getTo());
            }
        }
        log.info(sb.toString());
    }

    /**
     * 沿 path 递归挂载子结果（同层并行）
     */
    private void attachPathResults(List<Map<String, Object>> records,
                                    List<OntologyRelation> path, int hop) {
        if (hop >= path.size()) return;

        OntologyRelation rel = path.get(hop);
        String resultKey = deriveKeyFromEntity(rel.getTo());

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

                    String resultKey = deriveKeyFromEntity(rel.getTo());

                    // 如果已有该 key，跳过（避免重复查询）
                    if (record.containsKey(resultKey)) continue;

                    long nodeStartTime = System.currentTimeMillis();

                    // 传递父记录给 Gateway，支持从父记录获取额外参数
                    List<Map<String, Object>> children =
                        gatewayRegistry.getGateway(rel.getTo())
                            .queryByFieldWithContext(rel.getVia().get("target_field"), childValue, record);

                    // 节点遍历日志
                    log.info("  ├─ 节点 {} | key={} | value={} | 返回 {} 条 | 耗时 {}ms",
                        rel.getTo(),
                        rel.getVia().get("target_field"),
                        childValue,
                        children.size(),
                        System.currentTimeMillis() - nodeStartTime);

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
     * 从目标实体名推导结果 key
     * ContractNode -> contractNodes（首字母小写 + 复数）
     * SubOrder -> subOrders
     */
    private String deriveKeyFromEntity(String entityName) {
        // 首字母小写
        String camelCase = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1);
        // 加复数
        return camelCase + "s";
    }
}
