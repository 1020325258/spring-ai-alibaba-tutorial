package com.yycome.sreagent.domain.ontology.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 查询范围枚举
 * 用于指定本体论查询的目标实体
 */
@Getter
@RequiredArgsConstructor
public enum QueryScope {

    DEFAULT("default", "默认展开，按 defaultDepth 递归展开所有出边"),
    LIST("list", "仅返回起始实体本身，不展开关联"),

    // 领域实体
    CONTRACT("Contract", "展开到合同实体"),
    CONTRACT_NODE("ContractNode", "展开到合同节点"),
    CONTRACT_QUOTATION_RELATION("ContractQuotationRelation", "展开到签约单据"),
    CONTRACT_FIELD("ContractField", "展开到合同字段"),
    CONTRACT_INSTANCE("ContractInstance", "展开到合同实例"),
    CONTRACT_CONFIG("ContractConfig", "展开到配置表"),
    CONTRACT_USER("ContractUser", "展开到合同签约人"),
    SUB_ORDER("SubOrder", "展开到S单");

    private final String value;
    private final String description;

    /**
     * 从字符串解析 QueryScope
     * 支持枚举名称或 value 值
     */
    public static QueryScope fromString(String value) {
        if (value == null) return DEFAULT;

        // 先尝试按枚举名称解析
        for (QueryScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value) || scope.value.equalsIgnoreCase(value)) {
                return scope;
            }
        }
        // 如果都不匹配，可能是一个自定义的实体名，返回 null 表示需要动态处理
        return null;
    }

    /**
     * 获取目标实体名称（用于动态查询）
     */
    public String getTargetEntity() {
        return this == DEFAULT || this == LIST ? null : this.value;
    }
}
