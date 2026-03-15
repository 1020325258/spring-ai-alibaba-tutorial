# 本体论查询引擎数据驱动化 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 重构 OntologyQueryTool，使新增实体只需 YAML + Gateway，OntologyQueryTool 零改动。

**Architecture:** 新增 `OntologyQueryEngine` 承接所有查询执行逻辑（图遍历、并行执行、层级组装）；在 `EntityRegistry` 中新增 `findRelationPath` 方法返回 relation 链；`OntologyQueryTool` 精简为约 40 行的薄层；`OntologyEntity` 扩展 `lookupStrategies`/`displayName`/`aliases` 字段。

**Tech Stack:** Java 21, Spring Boot, Jackson YAML, JUnit 5, Mockito, CompletableFuture

**Verify command（每个 Task 完成后必须运行）：**
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn test -Dtest="ContractOntologyIT" -pl 05-SREmate
```

---

### Task 1：新增 LookupStrategy 模型 + 扩展 OntologyEntity

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/LookupStrategy.java`
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyEntity.java`

**Step 1: 创建 LookupStrategy**

```java
// LookupStrategy.java
package com.yycome.sremate.domain.ontology.model;

import lombok.Data;

/**
 * 实体查询策略：根据 value 匹配 pattern，决定传给 Gateway 的 fieldName
 */
@Data
public class LookupStrategy {
    private String field;    // 传给 gateway.queryByField 的字段名
    private String pattern;  // value 匹配正则（Java regex）
}
```

**Step 2: 扩展 OntologyEntity**

将 `OntologyEntity.java` 替换为：

```java
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
```

**Step 3: 编译验证（不跑测试，只验证编译）**

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
cd 05-SREmate && mvn compile -q
```
Expected: 无报错输出

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/LookupStrategy.java \
        05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyEntity.java
git commit -m "feat: 扩展 OntologyEntity 模型，新增 LookupStrategy/displayName/aliases"
```

---

### Task 2：更新 domain-ontology.yaml

**Files:**
- Modify: `05-SREmate/src/main/resources/ontology/domain-ontology.yaml`

**Step 1: 替换 YAML 内容**

```yaml
entities:
  - name: Order
    displayName: "订单"
    aliases: ["项目订单"]
    description: "项目订单，纯数字编号"
    defaultDepth: 2
    lookupStrategies:
      - field: projectOrderId
        pattern: "^\\d{15,}$"
    attributes:
      - { name: projectOrderId, type: string, description: "订单号，纯数字" }

  - name: Contract
    displayName: "合同"
    aliases: ["合同数据", "合同信息"]
    description: "合同实体，C前缀编号"
    table: contract
    defaultDepth: 2
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
      - field: projectOrderId
        pattern: "^\\d{15,}$"
    attributes:
      - { name: contractCode,   type: string, description: "合同编号，C前缀+数字" }
      - { name: contractType,   type: enum,   description: "合同类型" }
      - { name: status,         type: string, description: "合同状态" }
      - { name: projectOrderId, type: string, description: "所属订单号" }

  - name: ContractNode
    displayName: "合同节点"
    aliases: ["节点", "流程节点", "合同流程"]
    description: "合同节点/流程节点"
    table: contract_node
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: contractCode, type: string }
      - { name: nodeType,     type: string }
      - { name: fireTime,     type: string }

  - name: ContractQuotationRelation
    displayName: "签约单据"
    aliases: ["合同签约对象", "关联单据", "签约单"]
    description: "合同签约的单据对象，存储合同关联的报价单和S单"
    table: contract_quotation_relation
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: contractCode, type: string }
      - { name: bill_code,    type: string, description: "签约单据编号" }
      - { name: bind_type,    type: string }
      - { name: status,       type: string }

  - name: ContractField
    displayName: "合同字段"
    aliases: ["合同扩展字段", "字段数据"]
    description: "合同字段数据，按contractCode取模分10张表"
    table: contract_field_sharding
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: contractCode, type: string }
      - { name: field_key,    type: string }
      - { name: field_value,  type: string }

  - name: BudgetBill
    displayName: "报价单"
    aliases: ["报价", "预算单"]
    description: "报价单，挂在订单下"
    defaultDepth: 1
    lookupStrategies:
      - field: projectOrderId
        pattern: "^\\d{15,}$"
    attributes:
      - { name: billCode,       type: string, description: "报价单编号" }
      - { name: projectOrderId, type: string, description: "所属订单号" }

  - name: SubOrder
    displayName: "S单"
    aliases: ["子单", "签约子单"]
    description: "S单，可从报价单或合同两个维度关联"
    defaultDepth: 0
    lookupStrategies:
      - field: quotationOrderNo
        pattern: ".*"
    attributes:
      - { name: orderNo,          type: string }
      - { name: quotationOrderNo, type: string }
      - { name: contractCode,     type: string }

  - name: ContractForm
    displayName: "版式"
    aliases: ["版式数据", "form_id", "版式ID"]
    description: "合同版式数据"
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: contractCode, type: string }

  - name: ContractConfig
    displayName: "配置表"
    aliases: ["合同配置", "合同配置表"]
    description: "合同城市公司配置数据"
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
    attributes:
      - { name: contractCode, type: string }

relations:
  - from: Order
    to: Contract
    label: has_contracts
    domain: contract
    via: { source_field: projectOrderId, target_field: projectOrderId }

  - from: Order
    to: BudgetBill
    label: has_budget_bills
    domain: quote
    via: { source_field: projectOrderId, target_field: projectOrderId }

  - from: Contract
    to: ContractNode
    label: has_nodes
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractQuotationRelation
    label: has_signed_objects
    domain: contract
    description: "合同签约的单据对象"
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractField
    label: has_fields
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractForm
    label: has_form
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractConfig
    label: has_config
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: BudgetBill
    to: SubOrder
    label: splits_into
    domain: quote
    description: "报价单拆分形成S单"
    via: { source_field: billCode, target_field: quotationOrderNo }

  - from: ContractQuotationRelation
    to: SubOrder
    label: references_sub_order
    domain: contract
    via: { source_field: bill_code, target_field: orderNo }
```

**Step 2: 编译验证**

```bash
mvn compile -q
```
Expected: 无报错

**Step 3: 运行 EntityRegistryTest 确保 YAML 加载正常**

```bash
mvn test -Dtest="EntityRegistryTest" -pl 05-SREmate
```
Expected: PASS

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/resources/ontology/domain-ontology.yaml
git commit -m "feat: YAML 新增 lookupStrategies/displayName/aliases 字段"
```

---

### Task 3：EntityRegistry 新增 findRelationPath 和 getOutgoingRelations

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/service/EntityRegistry.java`
- Modify: `05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/service/EntityRegistryTest.java`

**Step 1: 写失败测试**

在 `EntityRegistryTest.java` 中追加：

```java
@Test
void findRelationPath_order_to_signedObjects_shouldReturnTwoHops() {
    List<OntologyRelation> path = entityRegistry.findRelationPath("Order", "ContractQuotationRelation");
    assertThat(path).hasSize(2);
    assertThat(path.get(0).getLabel()).isEqualTo("has_contracts");
    assertThat(path.get(1).getLabel()).isEqualTo("has_signed_objects");
}

@Test
void findRelationPath_contract_to_contractNode_shouldReturnOneHop() {
    List<OntologyRelation> path = entityRegistry.findRelationPath("Contract", "ContractNode");
    assertThat(path).hasSize(1);
    assertThat(path.get(0).getLabel()).isEqualTo("has_nodes");
}

@Test
void findRelationPath_noPath_shouldReturnNull() {
    List<OntologyRelation> path = entityRegistry.findRelationPath("BudgetBill", "ContractNode");
    assertThat(path).isNull();
}

@Test
void getOutgoingRelations_contract_shouldReturnFiveRelations() {
    List<OntologyRelation> outgoing = entityRegistry.getOutgoingRelations("Contract");
    assertThat(outgoing).hasSizeGreaterThanOrEqualTo(5);
    assertThat(outgoing).extracting(OntologyRelation::getLabel)
        .contains("has_nodes", "has_fields", "has_signed_objects", "has_form", "has_config");
}

@Test
void getEntity_shouldReturnCorrectEntity() {
    OntologyEntity entity = entityRegistry.getEntity("Contract");
    assertThat(entity).isNotNull();
    assertThat(entity.getDisplayName()).isEqualTo("合同");
    assertThat(entity.getLookupStrategies()).hasSize(2);
}
```

**Step 2: 运行测试，确认失败**

```bash
mvn test -Dtest="EntityRegistryTest" -pl 05-SREmate
```
Expected: FAIL — `findRelationPath`/`getOutgoingRelations`/`getEntity` 方法不存在

**Step 3: 在 EntityRegistry 中添加方法**

在 `EntityRegistry.java` 中追加以下方法（在 `getSummaryForPrompt` 之后）：

```java
/**
 * BFS 找从 from 到 to 的最短 relation 链。
 * 返回 relation 列表（按跳顺序），找不到返回 null。
 */
public List<OntologyRelation> findRelationPath(String from, String to) {
    if (from.equals(to)) return Collections.emptyList();

    // BFS：队列中存储"到达当前节点所经过的 relation 链"
    Queue<List<OntologyRelation>> queue = new LinkedList<>();
    Set<String> visited = new HashSet<>();
    visited.add(from);

    // 初始：从 from 出发的所有出边各自作为一条路径入队
    for (OntologyRelation rel : getOutgoingRelations(from)) {
        List<OntologyRelation> path = new ArrayList<>();
        path.add(rel);
        queue.add(path);
        if (rel.getTo().equals(to)) return path;
        visited.add(rel.getTo());
    }

    while (!queue.isEmpty()) {
        List<OntologyRelation> currentPath = queue.poll();
        String currentNode = currentPath.get(currentPath.size() - 1).getTo();

        for (OntologyRelation rel : getOutgoingRelations(currentNode)) {
            if (visited.contains(rel.getTo())) continue;
            visited.add(rel.getTo());

            List<OntologyRelation> newPath = new ArrayList<>(currentPath);
            newPath.add(rel);
            if (rel.getTo().equals(to)) return newPath;
            queue.add(newPath);
        }
    }
    return null; // 不可达
}

/**
 * 获取某实体的所有出边关系
 */
public List<OntologyRelation> getOutgoingRelations(String entityName) {
    return ontology.getRelations().stream()
        .filter(r -> r.getFrom().equals(entityName))
        .toList();
}

/**
 * 按名称查找实体
 */
public OntologyEntity getEntity(String name) {
    return ontology.getEntities().stream()
        .filter(e -> e.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知实体: " + name +
            "，可用实体: " + ontology.getEntities().stream()
                .map(OntologyEntity::getName).toList()));
}
```

还需要在 `EntityRegistry.java` 顶部补充 import：

```java
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
```

**Step 4: 运行测试，确认通过**

```bash
mvn test -Dtest="EntityRegistryTest" -pl 05-SREmate
```
Expected: PASS

**Step 5: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/service/EntityRegistry.java \
        05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/service/EntityRegistryTest.java
git commit -m "feat: EntityRegistry 新增 findRelationPath/getOutgoingRelations/getEntity"
```

---

### Task 4：创建 OntologyQueryEngine

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/engine/OntologyQueryEngine.java`
- Create: `05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/engine/OntologyQueryEngineTest.java`

**Step 1: 写失败测试**

创建 `OntologyQueryEngineTest.java`：

```java
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

    // ── matchStrategy 测试 ───────────────────────────────

    @Test
    void matchStrategy_contractCode_shouldMatchCPrefix() {
        OntologyEntity contract = makeEntity("Contract", 2, "contractCode", "^C\\d+");
        LookupStrategy s2 = new LookupStrategy();
        s2.setField("projectOrderId");
        s2.setPattern("^\\d{15,}$");
        contract.setLookupStrategies(List.of(contract.getLookupStrategies().get(0), s2));

        when(entityRegistry.getEntity("Contract")).thenReturn(contract);
        Map<String, Object> result = engine.query("Contract", "C1767173898135504", null);
        // 不抛异常即说明 matchStrategy 正确选择了 contractCode
        // （Gateway mock 返回空，引擎返回 null）
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
        // 设置实体：Contract(depth=1) → has_nodes → ContractNode(depth=0)
        OntologyEntity contractEntity = makeEntity("Contract", 1, "contractCode", "^C\\d+");
        OntologyRelation hasNodes = makeRelation("Contract", "ContractNode",
                                                  "has_nodes", "contractCode", "contractCode");

        when(entityRegistry.getEntity("Contract")).thenReturn(contractEntity);
        when(entityRegistry.getOutgoingRelations("Contract")).thenReturn(List.of(hasNodes));
        when(entityRegistry.getOutgoingRelations("ContractNode")).thenReturn(List.of());

        when(gatewayRegistry.getGateway("Contract")).thenReturn(contractGateway);
        when(gatewayRegistry.getGateway("ContractNode")).thenReturn(nodeGateway);
        when(contractGateway.queryByField("contractCode", "C123"))
            .thenReturn(List.of(Map.of("contractCode", "C123", "type", 8)));
        when(nodeGateway.queryByField("contractCode", "C123"))
            .thenReturn(List.of(Map.of("nodeType", 1, "fireTime", "2024-01-01")));

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
        EntityDataGateway signedObjectsGateway = mock(EntityDataGateway.class);

        when(gatewayRegistry.getGateway("Order")).thenReturn(orderGateway);
        when(gatewayRegistry.getGateway("ContractQuotationRelation")).thenReturn(signedObjectsGateway);

        when(orderGateway.queryByField("projectOrderId", "825123110000002753"))
            .thenReturn(List.of(
                Map.of("contractCode", "C1", "type", 8),
                Map.of("contractCode", "C2", "type", 3)
            ));
        when(signedObjectsGateway.queryByField("contractCode", "C1"))
            .thenReturn(List.of(Map.of("billCode", "GBILL001")));
        when(signedObjectsGateway.queryByField("contractCode", "C2"))
            .thenReturn(List.of(Map.of("billCode", "GBILL002")));

        Map<String, Object> result = engine.query("Order", "825123110000002753",
                                                   "ContractQuotationRelation");

        assertThat(result).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contracts = (List<Map<String, Object>>) result.get("records");
        assertThat(contracts).hasSize(2);
        assertThat(contracts.get(0)).containsKey("signedObjects");
        assertThat(contracts.get(1)).containsKey("signedObjects");
    }

    // ── deriveKey 测试 ────────────────────────────────────

    @Test
    void query_scoped_pathNotFound_shouldThrow() {
        OntologyEntity entity = makeEntity("BudgetBill", 1, "projectOrderId", "^\\d{15,}$");
        when(entityRegistry.getEntity("BudgetBill")).thenReturn(entity);
        when(entityRegistry.findRelationPath("BudgetBill", "ContractNode")).thenReturn(null);
        when(gatewayRegistry.getGateway("BudgetBill")).thenReturn(mock(EntityDataGateway.class));
        when(gatewayRegistry.getGateway("BudgetBill").queryByField(anyString(), any()))
            .thenReturn(List.of(Map.of("billCode", "B1")));

        assertThatThrownBy(() -> engine.query("BudgetBill", "825123110000002753", "ContractNode"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("找不到路径");
    }
}
```

**Step 2: 运行测试，确认编译失败**

```bash
mvn test -Dtest="OntologyQueryEngineTest" -pl 05-SREmate
```
Expected: FAIL — `OntologyQueryEngine` 不存在

**Step 3: 实现 OntologyQueryEngine**

创建 `OntologyQueryEngine.java`：

```java
package com.yycome.sremate.domain.ontology.engine;

import com.yycome.sremate.domain.ontology.model.LookupStrategy;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本体论查询执行引擎
 * 完全由 YAML 关系图驱动，支持 default 展开和 scoped 路径查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryEngine {

    private final EntityRegistry entityRegistry;
    private final EntityGatewayRegistry gatewayRegistry;

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    /**
     * 对外唯一入口
     *
     * @param entityName  起始实体名（Order / Contract / BudgetBill）
     * @param value       标识值（订单号 / 合同号等）
     * @param queryScope  目标实体名（ContractNode 等），null 或 "default" 则按 defaultDepth 展开
     * @return 层级结构的查询结果，起始实体无数据时返回 null
     */
    public Map<String, Object> query(String entityName, String value, String queryScope) {
        OntologyEntity entity = entityRegistry.getEntity(entityName);
        LookupStrategy strategy = matchStrategy(entity, value);

        List<Map<String, Object>> records =
            gatewayRegistry.getGateway(entityName).queryByField(strategy.getField(), value);

        if (records.isEmpty()) return null;

        if (queryScope == null || "default".equals(queryScope)) {
            expandDefault(entityName, records, entity.getDefaultDepth());
        } else {
            List<OntologyRelation> path = entityRegistry.findRelationPath(entityName, queryScope);
            if (path == null) {
                throw new IllegalArgumentException(
                    "找不到路径: " + entityName + " → " + queryScope +
                    "，请检查 domain-ontology.yaml 中的关系定义");
            }
            attachPathResults(records, path, 0);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryEntity", entityName);
        result.put("queryValue", value);
        result.put("records", records);
        return result;
    }

    /**
     * 按 defaultDepth 递归展开所有出边（同层并行）
     */
    private void expandDefault(String entityName, List<Map<String, Object>> records, int depth) {
        if (depth <= 0) return;

        List<OntologyRelation> outgoing = entityRegistry.getOutgoingRelations(entityName);
        if (outgoing.isEmpty()) return;

        // 对每条记录，并行展开所有出边
        List<CompletableFuture<Void>> futures = records.stream()
            .map(record -> CompletableFuture.runAsync(() -> {
                for (OntologyRelation rel : outgoing) {
                    Object childValue = record.get(rel.getVia().get("source_field"));
                    if (childValue == null) continue;
                    List<Map<String, Object>> children =
                        gatewayRegistry.getGateway(rel.getTo())
                            .queryByField(rel.getVia().get("target_field"), childValue);
                    expandDefault(rel.getTo(), children, depth - 1);
                    record.put(deriveKey(rel.getLabel()), children);
                }
            }, executor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 沿 path 递归挂载子结果（同层并行）
     */
    private void attachPathResults(List<Map<String, Object>> records,
                                    List<OntologyRelation> path, int hop) {
        if (hop >= path.size()) return;

        OntologyRelation rel = path.get(hop);
        String resultKey = deriveKey(rel.getLabel());

        List<CompletableFuture<Void>> futures = records.stream()
            .map(record -> CompletableFuture.runAsync(() -> {
                Object childValue = record.get(rel.getVia().get("source_field"));
                if (childValue == null) return;
                List<Map<String, Object>> children =
                    gatewayRegistry.getGateway(rel.getTo())
                        .queryByField(rel.getVia().get("target_field"), childValue);
                attachPathResults(children, path, hop + 1);
                record.put(resultKey, children);
            }, executor))
            .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 匹配 value 对应的 LookupStrategy
     */
    private LookupStrategy matchStrategy(OntologyEntity entity, String value) {
        if (entity.getLookupStrategies() == null || entity.getLookupStrategies().isEmpty()) {
            throw new IllegalStateException("实体 " + entity.getName() + " 未配置 lookupStrategies");
        }
        return entity.getLookupStrategies().stream()
            .filter(s -> value.matches(s.getPattern()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "无法识别的 value 格式: " + value +
                "，实体 " + entity.getName() + " 支持的格式: " +
                entity.getLookupStrategies().stream()
                    .map(LookupStrategy::getPattern).toList()));
    }

    /**
     * 从 relation label 推导结果 key
     * has_signed_objects → signedObjects
     * splits_into → subOrders（特殊处理：to 实体名小驼峰 + s）
     */
    private String deriveKey(String label) {
        String stripped = label.startsWith("has_") ? label.substring(4) : label;
        // snake_case → camelCase
        String[] parts = stripped.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }
}
```

**Step 4: 运行测试，确认通过**

```bash
mvn test -Dtest="OntologyQueryEngineTest" -pl 05-SREmate
```
Expected: PASS（注意：`splits_into` → `subOrders` 测试可能需要微调 deriveKey 逻辑）

**Step 5: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/engine/OntologyQueryEngine.java \
        05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/engine/OntologyQueryEngineTest.java
git commit -m "feat: 新增 OntologyQueryEngine，实现数据驱动的图遍历查询"
```

---

### Task 5：精简 OntologyQueryTool

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/OntologyQueryTool.java`

**Step 1: 运行基线测试，记录当前状态**

```bash
mvn test -Dtest="ContractOntologyIT" -pl 05-SREmate
```
Expected: 11/11 PASS（基线，改造前必须通过）

**Step 2: 替换 OntologyQueryTool.java 全部内容**

```java
package com.yycome.sremate.trigger.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yycome.sremate.domain.ontology.engine.OntologyQueryEngine;
import com.yycome.sremate.infrastructure.annotation.DataQueryTool;
import com.yycome.sremate.infrastructure.service.ToolExecutionTemplate;
import com.yycome.sremate.infrastructure.service.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 本体论驱动的统一查询工具（薄层）
 * 所有执行逻辑委托给 OntologyQueryEngine
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OntologyQueryTool {

    private final OntologyQueryEngine engine;
    private final ObjectMapper objectMapper;

    @Tool(description = """
        【本体论智能查询】根据起始实体和值，自动查询关联数据。

        参数：
        - entity: 起始实体类型（Order / Contract / BudgetBill）
        - value: 起始值（订单号或合同号）
        - queryScope: 目标实体名（可选）
          - 留空或 "default"：按默认深度展开全部关联
          - "ContractNode"：仅查合同节点
          - "ContractQuotationRelation"：仅查签约单据
          - "ContractField"：仅查合同字段
          - "ContractForm"：仅查版式数据
          - "ContractConfig"：仅查配置表
          - "BudgetBill"：仅查报价单
          - "SubOrder"：仅查S单（需通过中间实体路径）

        示例：
        - "825...下的合同数据" → entity=Order, value=825..., queryScope=default
        - "C176...的版式"     → entity=Contract, value=C176..., queryScope=ContractForm
        - "C176...的配置表"   → entity=Contract, value=C176..., queryScope=ContractConfig
        - "826...的报价单"    → entity=BudgetBill, value=826...
        """)
    @DataQueryTool
    public String ontologyQuery(String entity, String value, String queryScope) {
        return ToolExecutionTemplate.execute("ontologyQuery", () -> {
            log.info("[OntologyQueryTool] entity={}, value={}, scope={}", entity, value, queryScope);
            Map<String, Object> result = engine.query(entity, value, queryScope);
            if (result == null) return ToolResult.notFound(entity, value);
            return objectMapper.writeValueAsString(result);
        });
    }
}
```

**Step 3: 编译验证**

```bash
mvn compile -q
```
Expected: 无报错

**Step 4: 运行 ContractOntologyIT（关键验收）**

```bash
mvn test -Dtest="ContractOntologyIT" -pl 05-SREmate
```
Expected: 11/11 PASS

**Step 5: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/OntologyQueryTool.java
git commit -m "refactor: OntologyQueryTool 精简为薄层，委托 OntologyQueryEngine 执行"
```

---

### Task 6：更新 sre-agent.md 提示词

**Files:**
- Modify: `05-SREmate/src/main/resources/prompts/sre-agent.md`

**Step 1: 更新 queryScope 参数描述**

将提示词中 `queryScope` 说明从旧的 `form`/`nodes` 等字符串更新为实体名：

找到"可用工具 → ontologyQuery → queryScope"说明部分，替换为：

```markdown
  - queryScope: 查询范围（可选）
    - `default`: 使用实体默认深度（推荐）
    - `ContractNode`: 仅查节点关系
    - `ContractField`: 仅查字段关系
    - `ContractQuotationRelation`: 仅查签约单据
    - `ContractForm`: 仅查版式数据
    - `ContractConfig`: 仅查配置表数据
    - `BudgetBill`: 仅查报价单
```

同时更新决策表中 `queryScope` 相关示例（`form` → `ContractForm`，`config` → `ContractConfig` 等）。

**Step 2: 运行 ContractOntologyIT 回归验证**

```bash
mvn test -Dtest="ContractOntologyIT" -pl 05-SREmate
```
Expected: 11/11 PASS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/resources/prompts/sre-agent.md
git commit -m "docs: 更新 sre-agent.md，queryScope 改为目标实体名"
```

---

### Task 7：全量验收 + Push

**Step 1: 运行所有非集成测试**

```bash
mvn test -Dtest="EntityRegistryTest,OntologyQueryEngineTest,ToolExecutionTemplateTest,ToolResultTest,ObservabilityAspectAnnotationTest" -pl 05-SREmate
```
Expected: 全部 PASS

**Step 2: 运行 ContractOntologyIT 最终验收**

```bash
mvn test -Dtest="ContractOntologyIT" -pl 05-SREmate
```
Expected: 11/11 PASS

**Step 3: Push**

```bash
git push
```

---

## 新增实体 SOP（改造完成后）

改造完成后，新增实体只需两步，无需改动任何 Java 代码（除 Gateway 本身）：

**Step 1：YAML 添加实体和关系**
```yaml
entities:
  - name: NewEntity
    displayName: "新实体中文名"
    aliases: ["别名1"]
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"

relations:
  - from: Contract
    to: NewEntity
    label: has_new_entities
    via: { source_field: contractCode, target_field: contractCode }
```

**Step 2：实现 Gateway**
```java
@Component
public class NewEntityGateway implements EntityDataGateway {
    @PostConstruct public void init() { registry.register(this); }
    @Override public String getEntityName() { return "NewEntity"; }
    @Override
    public List<Map<String, Object>> queryByField(String field, Object value) {
        // 实现查询逻辑
    }
}
```

`OntologyQueryTool` 和 `OntologyQueryEngine` 无需任何修改。
