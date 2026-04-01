package com.yycome.sreagent.domain.ontology.model;

import lombok.Data;
import java.util.List;

/**
 * 本体实体定义
 */
@Data
public class OntologyEntity {
    private String name;
    private String displayName;                     // 中文显示名，注入 system prompt
    private List<String> aliases;                   // 中文别名列表
    private String description;
    private String table;
    private String sourceType;                      // 数据源类型：db / http
    private String endpoint;                         // HTTP 接口 ID（当 sourceType=http 时使用）
    private String flattenPath;                       // 数组展平路径，如 "data[].items[]"
    private List<OntologyAttribute> attributes;
    private List<LookupStrategy> lookupStrategies;  // 替换原 lookupField，支持多格式入口
}
