package com.yycome.sremate.domain.ontology.model;

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
    private List<OntologyAttribute> attributes;
    private int defaultDepth = 2;
    private List<LookupStrategy> lookupStrategies;  // 替换原 lookupField，支持多格式入口
}
