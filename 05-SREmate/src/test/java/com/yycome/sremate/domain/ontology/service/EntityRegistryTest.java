package com.yycome.sremate.domain.ontology.service;

import com.yycome.sremate.domain.ontology.model.OntologyModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityRegistryTest {

    private EntityRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        registry = new EntityRegistry(new ClassPathResource("ontology/domain-ontology.yaml"));
        registry.load();
    }

    @Test
    void load_shouldParseEntitiesAndRelations() {
        OntologyModel model = registry.getOntology();
        assertThat(model.getEntities()).isNotEmpty();
        assertThat(model.getRelations()).isNotEmpty();
    }

    @Test
    void load_shouldContractEntityExist() {
        assertThat(registry.getOntology().getEntities())
            .extracting("name")
            .contains("Contract", "ContractNode", "ContractQuotationRelation", "ContractField");
    }

    @Test
    void findPaths_orderToSubOrder_shouldReturnTwoPaths() {
        List<List<String>> paths = registry.findPaths("Order", "SubOrder");
        assertThat(paths).hasSize(2);
        assertThat(paths).anySatisfy(path -> assertThat(path).containsSequence("Order", "BudgetBill", "SubOrder"));
        assertThat(paths).anySatisfy(path -> assertThat(path).containsSequence("Order", "Contract", "ContractQuotationRelation", "SubOrder"));
    }

    @Test
    void getSummaryForPrompt_shouldContainRelationInfo() {
        String summary = registry.getSummaryForPrompt();
        assertThat(summary).contains("Contract");
        assertThat(summary).contains("has_signed_objects");
        assertThat(summary).contains("contractCode");
    }

    @Test
    void validation_relationWithUnknownEntity_shouldThrow() {
        // 这个测试验证 Schema 自洽性：relation 引用了不存在的实体应该报错
        // 通过加载一个故意错误的 YAML 来验证
        EntityRegistry badRegistry = new EntityRegistry(new ClassPathResource("ontology/test-invalid-ontology.yaml"));
        assertThatThrownBy(badRegistry::load)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("entity");
    }
}
