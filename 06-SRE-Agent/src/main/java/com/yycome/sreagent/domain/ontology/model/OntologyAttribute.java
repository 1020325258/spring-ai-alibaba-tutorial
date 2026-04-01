package com.yycome.sreagent.domain.ontology.model;

import lombok.Data;

/**
 * 本体属性定义
 */
@Data
public class OntologyAttribute {
    private String name;
    private String type;
    private String description;
    private String source;  // 字段来源路径，用于 YAML 驱动的字段解析
}
