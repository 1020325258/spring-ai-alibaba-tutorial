package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.LookupStrategy;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OntologyQueryEngineTest {

    @Mock EntityRegistry entityRegistry;
    @Mock EntityGatewayRegistry gatewayRegistry;
    @Mock EntityDataGateway contractGateway;
    @Mock EntityDataGateway nodeGateway;

    OntologyQueryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new OntologyQueryEngine(entityRegistry, gatewayRegistry);
    }

    // ── 工具方法 ───────────────────────────────────────────

    private OntologyEntity makeEntity(String name, int depth, String field, String pattern) {
        OntologyEntity e = new OntologyEntity();
        e.setName(name);
        e.setDefaultDepth(depth);
        LookupStrategy s = new LookupStrategy();
        s.setField(field);
        s.setPattern(pattern);
        e.setLookupStrategies(List.of(s));
        return e;
    }

    private OntologyRelation makeRelation(String from, String to, String label,
                                           String srcField, String tgtField) {
        OntologyRelation r = new OntologyRelation();
        r.setFrom(from);
        r.setTo(to);
        r.setLabel(label);
        r.setVia(Map.of("source_field", srcField, "target_field", tgtField));
        return r;
    }

    private Map<String, Object> mutableMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ── matchStrategy 测试 ───────────────────────────────

    @Test
    void matchStrategy_contractCode_shouldMatchCPrefix() {
        OntologyEntity contract = makeEntity("Contract", 2, "contractCode", "^C\\d+");
        LookupStrategy s2 = new LookupStrategy();
        s2.setField("projectOrderId");
        s2.setPattern("^\\d{15,}$");
        contract.setLookupStrategies(List.of(contract.getLookupStrategies().get(0), s2));

        when(entityRegistry.getEntity("Contract")).thenReturn(contract);
        when(gatewayRegistry.getGateway("Contract")).thenReturn(contractGateway);
        when(contractGateway.queryByField("contractCode", "C1767173898135504"))
            .thenReturn(List.of()); // 返回空列表，期望返回 null

        Map<String, Object> result = engine.query("Contract", "C1767173898135504", null);
        assertThat(result).isNull();
    }

    @Test
    void matchStrategy_noMatch_shouldThrow() {
        OntologyEntity entity = makeEntity("Contract", 2, "contractCode", "^C\\d+");
        when(entityRegistry.getEntity("Contract")).thenReturn(entity);
        assertThatThrownBy(() -> engine.query("Contract", "INVALID_FORMAT", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("无法识别的 value 格式");
    }

    // ── default 查询测试 ──────────────────────────────────

    @Test
    void query_default_singleLevel_shouldReturnRecordsWithChildren() {
        OntologyEntity contractEntity = makeEntity("Contract", 1, "contractCode", "^C\\d+");
        OntologyRelation hasNodes = makeRelation("Contract", "ContractNode",
                                                  "has_nodes", "contractCode", "contractCode");

        when(entityRegistry.getEntity("Contract")).thenReturn(contractEntity);
        when(entityRegistry.getOutgoingRelations("Contract")).thenReturn(List.of(hasNodes));

        when(gatewayRegistry.getGateway("Contract")).thenReturn(contractGateway);
        when(gatewayRegistry.getGateway("ContractNode")).thenReturn(nodeGateway);
        when(contractGateway.queryByField("contractCode", "C123"))
            .thenReturn(List.of(mutableMap("contractCode", "C123", "type", 8)));
        when(nodeGateway.queryByField("contractCode", "C123"))
            .thenReturn(List.of(mutableMap("nodeType", 1, "fireTime", "2024-01-01")));

        Map<String, Object> result = engine.query("Contract", "C123", null);

        assertThat(result).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contracts = (List<Map<String, Object>>) result.get("records");
        assertThat(contracts).hasSize(1);
        assertThat(contracts.get(0)).containsKey("nodes");
    }

    // ── scoped 查询测试 ──────────────────────────────────

    @Test
    void query_scoped_twoHops_shouldBuildHierarchy() {
        OntologyEntity orderEntity = makeEntity("Order", 2, "projectOrderId", "^\\d{15,}$");
        OntologyRelation hasContracts = makeRelation("Order", "Contract",
                                                      "has_contracts", "projectOrderId", "projectOrderId");
        OntologyRelation hasSignedObjects = makeRelation("Contract", "ContractQuotationRelation",
                                                          "has_signed_objects", "contractCode", "contractCode");

        when(entityRegistry.getEntity("Order")).thenReturn(orderEntity);
        when(entityRegistry.findRelationPath("Order", "ContractQuotationRelation"))
            .thenReturn(List.of(hasContracts, hasSignedObjects));

        EntityDataGateway orderGateway = mock(EntityDataGateway.class);
        EntityDataGateway contractGatewayForOrder = mock(EntityDataGateway.class);
        EntityDataGateway signedObjectsGateway = mock(EntityDataGateway.class);

        when(gatewayRegistry.getGateway("Order")).thenReturn(orderGateway);
        when(gatewayRegistry.getGateway("Contract")).thenReturn(contractGatewayForOrder);
        when(gatewayRegistry.getGateway("ContractQuotationRelation")).thenReturn(signedObjectsGateway);

        // Order 查询返回包含 projectOrderId 的记录
        when(orderGateway.queryByField("projectOrderId", "825123110000002753"))
            .thenReturn(List.of(
                mutableMap("projectOrderId", "825123110000002753", "orderType", 1),
                mutableMap("projectOrderId", "825123110000002753", "orderType", 2)
            ));

        // Contract 查询返回包含 contractCode 的记录
        when(contractGatewayForOrder.queryByField("projectOrderId", "825123110000002753"))
            .thenReturn(List.of(
                mutableMap("contractCode", "C1", "type", 8),
                mutableMap("contractCode", "C2", "type", 3)
            ));

        // SignedObjects 查询
        when(signedObjectsGateway.queryByField("contractCode", "C1"))
            .thenReturn(List.of(mutableMap("billCode", "GBILL001")));
        when(signedObjectsGateway.queryByField("contractCode", "C2"))
            .thenReturn(List.of(mutableMap("billCode", "GBILL002")));

        Map<String, Object> result = engine.query("Order", "825123110000002753",
                                                   "ContractQuotationRelation");

        assertThat(result).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) result.get("records");
        assertThat(orders).hasSize(2);
        // 每条 Order 记录应该有 contracts 子记录
        assertThat(orders.get(0)).containsKey("contracts");
        assertThat(orders.get(1)).containsKey("contracts");

        // contracts 子记录中应该有 signedObjects
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contracts0 = (List<Map<String, Object>>) orders.get(0).get("contracts");
        assertThat(contracts0).isNotEmpty();
        assertThat(contracts0.get(0)).containsKey("signedObjects");
    }

    // ── deriveKey 测试 ────────────────────────────────────

    @Test
    void query_scoped_pathNotFound_shouldThrow() {
        OntologyEntity entity = makeEntity("BudgetBill", 1, "projectOrderId", "^\\d{15,}$");
        when(entityRegistry.getEntity("BudgetBill")).thenReturn(entity);
        when(entityRegistry.findRelationPath("BudgetBill", "ContractNode")).thenReturn(null);
        when(gatewayRegistry.getGateway("BudgetBill")).thenReturn(mock(EntityDataGateway.class));
        when(gatewayRegistry.getGateway("BudgetBill").queryByField(anyString(), any()))
            .thenReturn(List.of(mutableMap("billCode", "B1")));

        assertThatThrownBy(() -> engine.query("BudgetBill", "825123110000002753", "ContractNode"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("找不到路径");
    }
}
