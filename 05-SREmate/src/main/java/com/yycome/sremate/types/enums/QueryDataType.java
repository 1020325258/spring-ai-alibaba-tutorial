package com.yycome.sremate.types.enums;

/**
 * 合同数据查询类型枚举
 * 对应 queryContractData 工具的 dataType 参数取值
 */
public enum QueryDataType {
    /** 全量数据：contract + contract_node + contract_user + contract_quotation_relation + contract_field_sharding */
    ALL,
    /** 节点日志：contract + contract_node + contract_log */
    CONTRACT_NODE,
    /** 字段数据：contract + contract_field_sharding_N（分表） */
    CONTRACT_FIELD,
    /** 签约人：contract + contract_user */
    CONTRACT_USER
}
