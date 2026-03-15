package com.yycome.sremate.domain.ontology.engine;

import java.util.List;
import java.util.Map;

/**
 * 实体数据网关接口
 * 每个实体实现一个，负责从数据库查询数据
 */
public interface EntityDataGateway {

    /**
     * 获取支持的实体名称
     */
    String getEntityName();

    /**
     * 根据字段查询实体数据
     * @param fieldName 字段名
     * @param value 字段值
     * @return 实体数据列表
     */
    List<Map<String, Object>> queryByField(String fieldName, Object value);

    /**
     * 批量查询 - 根据字段值列表查询
     * @param fieldName 字段名
     * @param values 字段值列表
     * @return 实体数据列表
     */
    default List<Map<String, Object>> queryByFieldBatch(String fieldName, List<?> values) {
        // 默认实现：逐个查询
        return values.stream()
            .flatMap(v -> queryByField(fieldName, v).stream())
            .toList();
    }
}
