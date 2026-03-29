package com.yycome.sreagent.domain.ontology.engine;

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

    /**
     * 根据字段查询实体数据（带父记录上下文）
     * 用于需要从父记录获取额外参数的场景（如 BudgetBill → SubOrder 需要 homeOrderNo + quotationOrderNo）
     *
     * @param fieldName 字段名
     * @param value 字段值
     * @param parentRecord 父记录（包含上游实体的完整数据）
     * @return 实体数据列表
     */
    default List<Map<String, Object>> queryByFieldWithContext(String fieldName, Object value, Map<String, Object> parentRecord) {
        // 默认实现：忽略 parentRecord，调用原方法
        return queryByField(fieldName, value);
    }

    /**
     * 根据字段查询实体数据（带额外参数）
     * 用于需要用户指定额外参数的场景（如 PersonalQuote 需要 subOrderNoList/billCodeList/changeOrderId）
     *
     * @param fieldName 字段名
     * @param value 字段值
     * @param extraParams 额外参数（key-value 形式）
     * @return 实体数据列表
     */
    default List<Map<String, Object>> queryWithExtraParams(String fieldName, Object value, Map<String, String> extraParams) {
        // 默认实现：忽略 extraParams，调用原方法
        return queryByField(fieldName, value);
    }
}
