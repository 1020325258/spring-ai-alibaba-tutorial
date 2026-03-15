package com.yycome.sremate.domain.ontology.service;

import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyModel;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
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

    @Test
    void findRelationPath_order_to_signedObjects_shouldReturnTwoHops() {
        List<OntologyRelation> path = registry.findRelationPath("Order", "ContractQuotationRelation");
        assertThat(path).hasSize(2);
        assertThat(path.get(0).getLabel()).isEqualTo("has_contracts");
        assertThat(path.get(1).getLabel()).isEqualTo("has_signed_objects");
    }

    @Test
    void findRelationPath_contract_to_contractNode_shouldReturnOneHop() {
        List<OntologyRelation> path = registry.findRelationPath("Contract", "ContractNode");
        assertThat(path).hasSize(1);
        assertThat(path.get(0).getLabel()).isEqualTo("has_nodes");
    }

    @Test
    void findRelationPath_noPath_shouldReturnNull() {
        List<OntologyRelation> path = registry.findRelationPath("BudgetBill", "ContractNode");
        assertThat(path).isNull();
    }

    @Test
    void getOutgoingRelations_contract_shouldReturnFiveRelations() {
        List<OntologyRelation> outgoing = registry.getOutgoingRelations("Contract");
        assertThat(outgoing).hasSizeGreaterThanOrEqualTo(5);
        assertThat(outgoing).extracting(OntologyRelation::getLabel)
            .contains("has_nodes", "has_fields", "has_signed_objects", "has_form", "has_config");
    }

    @Test
    void getEntity_shouldReturnCorrectEntity() {
        OntologyEntity entity = registry.getEntity("Contract");
        assertThat(entity).isNotNull();
        assertThat(entity.getDisplayName()).isEqualTo("合同");
        assertThat(entity.getLookupStrategies()).hasSize(2);
    }
}
