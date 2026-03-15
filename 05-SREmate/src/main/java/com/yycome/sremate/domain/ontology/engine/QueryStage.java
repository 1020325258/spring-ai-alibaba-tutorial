package com.yycome.sremate.domain.ontology.engine;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 查询阶段 - 同阶段的任务可以并行执行
 */
@Data
public class QueryStage {
    private int stage;                          // 阶段编号
    private String dependsOnField;              // 依赖的前序字段（null 表示初始阶段）
    private List<QueryTask> tasks = new ArrayList<>();

    public void addTask(QueryTask task) {
        tasks.add(task);
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
