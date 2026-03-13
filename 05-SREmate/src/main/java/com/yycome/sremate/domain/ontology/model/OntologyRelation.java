package com.yycome.sremate.domain.ontology.model;

import lombok.Data;
import java.util.Map;

/**
 * 本体关系定义
 */
@Data
public class OntologyRelation {
    private String from;
    private String to;
    private String label;
    private String domain;        // "contract" | "quote"
    private String description;
    private Map<String, String> via;   // {source_field, target_field}
}
