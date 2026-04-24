package com.yycome.sreagent.domain.ontology.engine;

import com.yycome.sreagent.domain.ontology.model.LookupStrategy;
import com.yycome.sreagent.domain.ontology.model.OntologyEntity;
import com.yycome.sreagent.domain.ontology.model.OntologyRelation;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 本体论查询执行引擎
 * 完全由 YAML 关系图驱动，支持 default 展开和 scoped 路径查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryEngine {

    // Scope 常量
    private static final String SCOPE_DEFAULT = "default";
    private static final String SCOPE_LIST = "list";

    // 结果 key 常量
    private static final String KEY_QUERY_ENTITY = "queryEntity";
    private static final String KEY_QUERY_VALUE = "queryValue";
    private static final String KEY_RECORDS = "records";

    private final EntityRegistry entityRegistry;
    private final EntityGatewayRegistry gatewayRegistry;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

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
        return query(entityName, value, queryScope, null);
    }

    /**
     * 对外唯一入口（带额外参数版本）
     *
     * @param entityName   起始实体名
     * @param value        标识值
     * @param queryScope   目标实体名
     * @param extraParams  额外参数（如 PersonalQuote 的 subOrderNoList/billCodeList/changeOrderId）
     * @return 层级结构的查询结果
     */
    public Map<String, Object> query(String entityName, String value, String queryScope, Map<String, String> extraParams) {
        if (shouldExpandRelations(queryScope)) {
            return queryWithExpansion(entityName, value, queryScope, extraParams);
        }
        return queryWithoutExpansion(entityName, value);
    }

    /**
     * 仅查询起始实体，不展开关联
     */
    private Map<String, Object> queryWithoutExpansion(String entityName, String value) {
        List<Map<String, Object>> records = queryStartEntity(entityName, value);
        if (records.isEmpty()) return null;
        return buildQueryResult(entityName, value, records);
    }

    /**
     * 查询起始实体并展开关联路径
     */
    private Map<String, Object> queryWithExpansion(String entityName, String value, String queryScope, Map<String, String> extraParams) {
        List<Map<String, Object>> records = queryStartEntity(entityName, value);
        if (records.isEmpty()) return null;

        List<List<OntologyRelation>> paths = buildRelationPaths(entityName, queryScope);
        logPathPlan(entityName, paths);
        attachMultiPathResults(records, paths, extraParams);

        return buildQueryResult(entityName, value, records);
    }

    /**
     * 查询起始实体
     *
     * @return 起始实体的记录列表
     */
    private List<Map<String, Object>> queryStartEntity(String entityName, String value) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        log.debug("▶ 起始节点 {} | key={} | value={}", entityName, strategy.getField(), value);
        long startTime = System.currentTimeMillis();

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        log.debug("  ↳ 返回 {} 条记录, 耗时 {}ms", records.size(), System.currentTimeMillis() - startTime);
        return records;
    }

    /**
     * 构建查询结果
     */
    private Map<String, Object> buildQueryResult(String entityName, String value, List<Map<String, Object>> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(KEY_QUERY_ENTITY, entityName);
        result.put(KEY_QUERY_VALUE, value);
        result.put(KEY_RECORDS, records);
        return result;
    }

    /**
     * 判断是否需要展开关联
     */
    private boolean shouldExpandRelations(String queryScope) {
        return queryScope != null
            && !SCOPE_DEFAULT.equals(queryScope)
            && !SCOPE_LIST.equals(queryScope);
    }

    /**
     * 构建关系路径列表
     *
     * @param entityName 起始实体名
     * @param queryScope 目标实体（支持逗号分隔的多目标）
     * @return 关系路径列表
     */
    private List<List<OntologyRelation>> buildRelationPaths(String entityName, String queryScope) {
        List<String> targets = Arrays.stream(queryScope.split(","))
            .map(String::trim)
            .toList();

        List<List<OntologyRelation>> paths = new ArrayList<>();
        for (String target : targets) {
            List<OntologyRelation> path = entityRegistry.findRelationPath(entityName, target);
            if (path == null) {
                throw new IllegalArgumentException(
                    "找不到路径: " + entityName + " -> " + target +
                    "，请检查 domain-ontology.yaml 中的关系定义");
            }
            paths.add(path);
        }
        return paths;
    }

    /**
     * 打印路径规划日志
     */
    private void logPathPlan(String startEntity, List<List<OntologyRelation>> paths) {
        String pathStr = paths.stream()
            .map(path -> path.stream()
                .map(rel -> String.format("-[%s→%s]-> %s",
                    rel.getVia().get("source_field"),
                    rel.getVia().get("target_field"),
                    rel.getTo()))
                .collect(Collectors.joining(" ")))
            .collect(Collectors.joining(" | "));
        log.debug("路径规划: {} → {}", startEntity, pathStr);
    }

    /**
     * 多目标路径展开
     * 对每条记录，展开所有目标路径，合并相同层级的查询结果
     *
     * @param extraParams 额外参数（用于首层关系查询，如 PersonalQuote）
     */
    private void attachMultiPathResults(List<Map<String, Object>> records,
                                         List<List<OntologyRelation>> paths,
                                         Map<String, String> extraParams) {
        // 按层级分组：hop -> 关系列表（去重）
        Map<Integer, Set<OntologyRelation>> hopRelations = new LinkedHashMap<>();
        for (List<OntologyRelation> path : paths) {
            for (int hop = 0; hop < path.size(); hop++) {
                hopRelations.computeIfAbsent(hop, k -> new LinkedHashSet<>()).add(path.get(hop));
            }
        }

        // 逐层展开（首层传递额外参数）
        attachLayer(records, hopRelations, 0, extraParams);
    }

    /**
     * 递归展开某一层的关系
     */
    private void attachLayer(List<Map<String, Object>> records,
                              Map<Integer, Set<OntologyRelation>> hopRelations,
                              int hop,
                              Map<String, String> extraParams) {
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

                    // 首层且存在额外参数时，使用 queryWithExtraParams（如 PersonalQuote）
                    // 其他情况使用 queryByFieldWithContext
                    List<Map<String, Object>> children;
                    if (hop == 0 && extraParams != null && !extraParams.isEmpty()) {
                        children = gatewayRegistry.getGateway(rel.getTo())
                            .queryWithExtraParams(rel.getVia().get("target_field"), childValue, extraParams);
                    } else {
                        children = gatewayRegistry.getGateway(rel.getTo())
                            .queryByFieldWithContext(rel.getVia().get("target_field"), childValue, record);
                    }

                    // 节点遍历日志
                    log.debug("  ├─ 节点 {} | key={} | value={} | 返回 {} 条 | 耗时 {}ms",
                        rel.getTo(),
                        rel.getVia().get("target_field"),
                        childValue,
                        children.size(),
                        System.currentTimeMillis() - nodeStartTime);

                    // 递归展开下一层（额外参数仅用于首层）
                    attachLayer(children, hopRelations, hop + 1, null);

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
