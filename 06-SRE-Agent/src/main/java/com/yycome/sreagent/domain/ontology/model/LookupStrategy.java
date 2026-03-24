package com.yycome.sreagent.domain.ontology.model;

import lombok.Data;

/**
 * 实体查询策略：根据 value 匹配 pattern，决定传给 Gateway 的 fieldName
 */
@Data
public class LookupStrategy {
    private String field;    // 传给 gateway.queryByField 的字段名
    private String pattern;  // value 匹配正则（Java regex）
}
