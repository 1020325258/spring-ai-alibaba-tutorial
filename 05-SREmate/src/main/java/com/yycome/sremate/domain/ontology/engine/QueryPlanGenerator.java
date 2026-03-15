package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询计划生成器
 * 根据本体定义和查询意图，生成分阶段的查询计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryPlanGenerator {

    private final EntityRegistry entityRegistry;

    /**
     * 生成查询计划
     * @param startEntity 起始实体
     * @param targetRelations 目标关系标签列表（null 表示查询所有关联）
     * @param maxDepth 最大查询深度
     * @return 分阶段的查询计划
     */
    public List<QueryStage> generatePlan(String startEntity, List<String> targetRelations, int maxDepth) {
        List<QueryStage> stages = new ArrayList<>();

        // 获取起始实体的默认深度
        OntologyEntity start = entityRegistry.getOntology().getEntities().stream()
            .filter(e -> e.getName().equals(startEntity))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未找到实体: " + startEntity));

        int actualDepth = Math.min(maxDepth, start.getDefaultDepth());
        log.info("[QueryPlanGenerator] 生成查询计划: startEntity={}, targetRelations={}, maxDepth={}, actualDepth={}",
            startEntity, targetRelations, maxDepth, actualDepth);

        // BFS 遍历关系图，生成查询阶段
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startEntity);
        visited.add(startEntity);

        int stageNum = 0;
        while (!queue.isEmpty() && stageNum < actualDepth) {
            String current = queue.poll();
            List<OntologyRelation> relations = findRelationsFrom(current);

            QueryStage stage = new QueryStage();
            stage.setStage(stageNum);
            stage.setDependsOnField(stageNum == 0 ? null : getPrimaryKeyField(current));

            for (OntologyRelation rel : relations) {
                // 过滤目标关系
                if (targetRelations != null && !targetRelations.isEmpty()
                    && !targetRelations.contains(rel.getLabel())) {
                    continue;
                }

                String targetEntity = rel.getTo();
                if (!visited.contains(targetEntity)) {
                    QueryTask task = new QueryTask();
                    task.setRelationLabel(rel.getLabel());
                    task.setFromEntity(current);
                    task.setToEntity(targetEntity);
                    task.setSourceField(rel.getVia().get("source_field"));
                    task.setTargetField(rel.getVia().get("target_field"));
                    stage.addTask(task);

                    visited.add(targetEntity);
                    queue.add(targetEntity);
                }
            }

            if (!stage.isEmpty()) {
                stages.add(stage);
            }
            stageNum++;
        }

        log.info("[QueryPlanGenerator] 生成 {} 个查询阶段", stages.size());
        return stages;
    }

    /**
     * 查找从指定实体出发的所有关系
     */
    private List<OntologyRelation> findRelationsFrom(String entityName) {
        return entityRegistry.getOntology().getRelations().stream()
            .filter(r -> r.getFrom().equals(entityName))
            .toList();
    }

    /**
     * 获取实体的主键字段名
     */
    private String getPrimaryKeyField(String entityName) {
        return switch (entityName) {
            case "Contract" -> "contractCode";
            case "Order" -> "projectOrderId";
            case "BudgetBill" -> "billCode";
            default -> "id";
        };
    }
}
