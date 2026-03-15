package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import lombok.Data;

/**
 * 单个查询任务
 */
@Data
public class QueryTask {
    private String relationLabel;       // 关系标签，如 has_nodes
    private String fromEntity;          // 源实体
    private String toEntity;            // 目标实体
    private String sourceField;         // 源字段
    private String targetField;         // 目标字段
    private Object paramValue;          // 查询参数值

    public static QueryTask fromRelation(OntologyRelation relation, Object paramValue) {
        QueryTask task = new QueryTask();
        task.setRelationLabel(relation.getLabel());
        task.setFromEntity(relation.getFrom());
        task.setToEntity(relation.getTo());
        task.setSourceField(relation.getVia().get("source_field"));
        task.setTargetField(relation.getVia().get("target_field"));
        task.setParamValue(paramValue);
        return task;
    }
}
