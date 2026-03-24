package com.yycome.sreagent.trigger.http;

import com.yycome.sreagent.domain.ontology.model.OntologyModel;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本体可视化 API
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OntologyController {

    private final EntityRegistry entityRegistry;

    @GetMapping("/ontology")
    public OntologyModel getOntology() {
        return entityRegistry.getOntology();
    }
}
