package com.yycome.sremate.domain.ontology.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 查询执行器
 * 负责并行执行查询计划
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryExecutor {

    private final EntityGatewayRegistry gatewayRegistry;

    /** 用于并行查询的线程池 */
    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(10);

    /**
     * 执行查询计划
     * @param stages 分阶段的查询计划
     * @param startValue 起始值（如订单号）
     * @return 聚合后的查询结果
     */
    public Map<String, Object> execute(List<QueryStage> stages, Object startValue) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("startValue", startValue);

        for (QueryStage stage : stages) {
            log.info("[QueryExecutor] 执行阶段 {}: {} 个任务", stage.getStage(), stage.getTasks().size());
            executeStage(stage, context);
        }

        return context;
    }

    /**
     * 执行单个阶段（同阶段任务并行）
     */
    private void executeStage(QueryStage stage, Map<String, Object> context) {
        List<QueryTask> tasks = stage.getTasks();

        if (tasks.size() == 1) {
            // 单任务直接执行
            QueryTask task = tasks.get(0);
            Object result = executeTask(task, context);
            context.put(task.getRelationLabel(), result);
        } else {
            // 多任务并行执行
            List<CompletableFuture<Map.Entry<String, Object>>> futures = tasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> {
                    Object result = executeTask(task, context);
                    return Map.entry(task.getRelationLabel(), result);
                }, queryExecutor))
                .toList();

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 收集结果
            futures.forEach(f -> {
                Map.Entry<String, Object> entry = f.join();
                context.put(entry.getKey(), entry.getValue());
            });
        }
    }

    /**
     * 执行单个查询任务
     */
    private Object executeTask(QueryTask task, Map<String, Object> context) {
        EntityDataGateway gateway = gatewayRegistry.getGateway(task.getToEntity());
        if (gateway == null) {
            log.warn("[QueryExecutor] 未找到网关: {}", task.getToEntity());
            return null;
        }

        // 获取参数值
        Object paramValue = task.getParamValue();
        if (paramValue == null && task.getSourceField() != null) {
            paramValue = context.get(task.getSourceField());
        }

        log.debug("[QueryExecutor] 执行任务: {} -> {}, field={}, param={}",
            task.getFromEntity(), task.getToEntity(), task.getTargetField(), paramValue);

        long start = System.currentTimeMillis();
        List<Map<String, Object>> result = gateway.queryByField(task.getTargetField(), paramValue);
        log.info("[QueryExecutor] 查询完成: {} ({}ms, {} 条记录)",
            task.getRelationLabel(), System.currentTimeMillis() - start, result.size());

        return result;
    }
}
