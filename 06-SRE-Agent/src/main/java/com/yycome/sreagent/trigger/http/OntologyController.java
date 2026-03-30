package com.yycome.sreagent.trigger.http;

import com.yycome.sreagent.domain.ontology.model.OntologyModel;
import com.yycome.sreagent.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 本体可视化 API
 * 为前端 HTML 页面提供本体数据
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OntologyController {

    private final EntityRegistry entityRegistry;

    /**
     * 返回完整的本体模型（实体 + 关系）
     * 供 ontology.html 可视化使用
     */
    @GetMapping("/ontology")
    public OntologyModel getOntology() {
        return entityRegistry.getOntology();
    }
}
