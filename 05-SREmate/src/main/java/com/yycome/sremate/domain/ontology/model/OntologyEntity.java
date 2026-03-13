package com.yycome.sremate.domain.ontology.model;

import lombok.Data;
import java.util.List;

/**
 * 本体实体定义
 */
@Data
public class OntologyEntity {
    private String name;
    private String description;
    private String table;                         // 对应数据库表名（可空）
    private List<OntologyAttribute> attributes;
}
