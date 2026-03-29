package com.yycome.sreagent.domain.ontology.model;

import lombok.Data;
import java.util.List;

/**
 * 本体模型，包含实体和关系定义
 */
@Data
public class OntologyModel {
    private List<OntologyEntity> entities;
    private List<OntologyRelation> relations;
}
