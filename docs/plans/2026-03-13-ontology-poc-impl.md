# Ontology-Driven Agent Query (PoC) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 以合同查询为 PoC，引入本体论基础设施（EntityRegistry + 本体 YAML），将 `queryContractData` 拆分为四个独立工具方法，并将本体摘要注入 system prompt，提升 agent 查询准确性。

**Architecture:** 本体 YAML 作为 single source of truth，EntityRegistry 启动时加载并校验，通过 `getSummaryForPrompt()` 注入 system prompt。ContractQueryTool 按实体拆分为四个独立 @Tool 方法，每个方法直接调用已有 ContractDao 方法。

**Tech Stack:** Spring Boot, SnakeYAML（已在类路径，Spring Boot 内置）, Spring AI @Tool, JdbcTemplate（DAO 已有）

**Design Doc:** `docs/plans/2026-03-13-ontology-agent-design.md`

---

## Phase 1：本体基础设施

### Task 1：OntologyModel POJO

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyModel.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyEntity.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyAttribute.java`
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/model/OntologyRelation.java`

**Step 1: 创建 OntologyAttribute**

```java
// OntologyAttribute.java
package com.yycome.sremate.domain.ontology.model;

import lombok.Data;

@Data
public class OntologyAttribute {
    private String name;
    private String type;
    private String description;
}
```

**Step 2: 创建 OntologyEntity**

```java
// OntologyEntity.java
package com.yycome.sremate.domain.ontology.model;

import lombok.Data;
import java.util.List;

@Data
public class OntologyEntity {
    private String name;
    private String description;
    private String table;                         // 对应数据库表名（可空）
    private List<OntologyAttribute> attributes;
}
```

**Step 3: 创建 OntologyRelation**

```java
// OntologyRelation.java
package com.yycome.sremate.domain.ontology.model;

import lombok.Data;
import java.util.Map;

@Data
public class OntologyRelation {
    private String from;
    private String to;
    private String label;
    private String domain;        // "contract" | "quote"
    private String description;
    private Map<String, String> via;   // {source_field, target_field}
}
```

**Step 4: 创建 OntologyModel**

```java
// OntologyModel.java
package com.yycome.sremate.domain.ontology.model;

import lombok.Data;
import java.util.List;

@Data
public class OntologyModel {
    private List<OntologyEntity> entities;
    private List<OntologyRelation> relations;
}
```

**Step 5: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/
git commit -m "feat(ontology): add OntologyModel POJOs"
```

---

### Task 2：domain-ontology.yaml

**Files:**
- Create: `05-SREmate/src/main/resources/ontology/domain-ontology.yaml`

**Step 1: 创建本体 YAML 文件**

```yaml
entities:
  - name: Order
    description: "项目订单，纯数字编号"
    attributes:
      - { name: projectOrderId, type: string, description: "订单号，纯数字" }

  - name: Contract
    description: "合同实体，C前缀编号"
    table: contract
    attributes:
      - { name: contractCode,  type: string, description: "合同编号，C前缀+数字" }
      - { name: contractType,  type: enum,   description: "合同类型：认购/正签/销售等" }
      - { name: status,        type: string, description: "合同状态" }

  - name: ContractNode
    description: "合同节点/流程节点"
    table: contract_node
    attributes:
      - { name: contractCode, type: string }
      - { name: nodeType,     type: string }
      - { name: fireTime,     type: string }

  - name: ContractQuotationRelation
    description: "合同签约的单据对象，存储合同关联的报价单和S单"
    table: contract_quotation_relation
    attributes:
      - { name: contractCode, type: string }
      - { name: bill_code,    type: string, description: "签约单据编号，可为报价单或S单" }
      - { name: bind_type,    type: string }
      - { name: status,       type: string }

  - name: ContractField
    description: "合同字段数据，按contractCode取模分10张表"
    table: contract_field_sharding
    attributes:
      - { name: contractCode, type: string }
      - { name: field_key,    type: string }
      - { name: field_value,  type: string }

  - name: BudgetBill
    description: "报价单，挂在订单下"
    attributes:
      - { name: billCode,       type: string, description: "报价单编号" }
      - { name: projectOrderId, type: string, description: "所属订单号" }

  - name: SubOrder
    description: "S单，可从报价单或合同两个维度关联"
    attributes:
      - { name: orderNo,          type: string }
      - { name: quotationOrderNo, type: string, description: "关联报价单的字段" }
      - { name: contractCode,     type: string, description: "关联合同的字段" }
      - { name: projectChangeNo,  type: string }
      - { name: mdmCode,          type: string }
      - { name: dueAmount,        type: number }

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
    description: "合同签约的单据对象，bill_code可指向报价单或S单"
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractField
    label: has_fields
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: BudgetBill
    to: SubOrder
    label: splits_into
    domain: quote
    description: "全量数据，报价单拆分形成S单"
    via: { source_field: billCode, target_field: quotationOrderNo }

  - from: ContractQuotationRelation
    to: SubOrder
    label: references_sub_order
    domain: contract
    description: "签约领域关联数据"
    via: { source_field: bill_code, target_field: orderNo }
```

**Step 2: Commit**

```bash
git add 05-SREmate/src/main/resources/ontology/domain-ontology.yaml
git commit -m "feat(ontology): add domain-ontology.yaml"
```

---

### Task 3：EntityRegistry（含 Schema 自洽性校验）

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/service/EntityRegistry.java`
- Test: `05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/service/EntityRegistryTest.java`

**Step 1: 写失败测试**

```java
// EntityRegistryTest.java
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
```

**Step 2: 运行测试，确认失败**

```bash
cd 05-SREmate
mvn test -pl . -Dtest=EntityRegistryTest -q 2>&1 | tail -5
```

期望：编译错误（EntityRegistry 不存在）

**Step 3: 创建 EntityRegistry**

```java
// EntityRegistry.java
package com.yycome.sremate.domain.ontology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yycome.sremate.domain.ontology.model.OntologyEntity;
import com.yycome.sremate.domain.ontology.model.OntologyModel;
import com.yycome.sremate.domain.ontology.model.OntologyRelation;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EntityRegistry {

    private final Resource ontologyResource;
    private OntologyModel ontology;

    public EntityRegistry(@Value("classpath:ontology/domain-ontology.yaml") Resource ontologyResource) {
        this.ontologyResource = ontologyResource;
    }

    @PostConstruct
    public void load() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        ontology = mapper.readValue(ontologyResource.getInputStream(), OntologyModel.class);
        validate();
        log.info("[EntityRegistry] 本体加载完成：{} 个实体，{} 条关系",
            ontology.getEntities().size(), ontology.getRelations().size());
    }

    /** Schema 自洽性校验：所有 relation 的 from/to 必须在 entities 中存在 */
    private void validate() {
        Set<String> entityNames = ontology.getEntities().stream()
            .map(OntologyEntity::getName)
            .collect(Collectors.toSet());

        for (OntologyRelation rel : ontology.getRelations()) {
            if (!entityNames.contains(rel.getFrom())) {
                throw new IllegalStateException(
                    "本体校验失败：relation 引用了不存在的 entity '" + rel.getFrom() + "'");
            }
            if (!entityNames.contains(rel.getTo())) {
                throw new IllegalStateException(
                    "本体校验失败：relation 引用了不存在的 entity '" + rel.getTo() + "'");
            }
        }
    }

    /** 返回本体模型（供可视化 API 使用） */
    public OntologyModel getOntology() {
        return ontology;
    }

    /**
     * 图遍历：返回从 from 到 to 的所有可达路径（节点名称列表）
     * 使用 BFS，避免循环
     */
    public List<List<String>> findPaths(String from, String to) {
        List<List<String>> result = new ArrayList<>();
        Queue<List<String>> queue = new LinkedList<>();
        queue.add(new ArrayList<>(List.of(from)));

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);

            if (current.equals(to)) {
                result.add(path);
                continue;
            }

            // 防止无限循环
            if (path.size() > 6) continue;

            for (OntologyRelation rel : ontology.getRelations()) {
                if (rel.getFrom().equals(current) && !path.contains(rel.getTo())) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(rel.getTo());
                    queue.add(newPath);
                }
            }
        }
        return result;
    }

    /**
     * 生成注入 system prompt 的本体摘要
     */
    public String getSummaryForPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("【数据模型关系】\n");
        for (OntologyRelation rel : ontology.getRelations()) {
            String via = rel.getVia() != null
                ? rel.getVia().getOrDefault("source_field", "?") + " → " +
                  rel.getVia().getOrDefault("target_field", "?")
                : "";
            String desc = rel.getDescription() != null ? "（" + rel.getDescription() + "）" : "";
            sb.append(String.format("- %s -[%s]-> %s  via: %s  [%s域]%s\n",
                rel.getFrom(), rel.getLabel(), rel.getTo(), via, rel.getDomain(), desc));
        }
        return sb.toString();
    }
}
```

**Step 4: 创建用于失败测试的无效 YAML（仅测试用）**

在 `05-SREmate/src/test/resources/ontology/test-invalid-ontology.yaml`：

```yaml
entities:
  - name: Order
    description: "测试用"
relations:
  - from: Order
    to: NonExistentEntity
    label: test
    domain: test
```

**Step 5: 运行测试**

```bash
mvn test -pl 05-SREmate -Dtest=EntityRegistryTest -q 2>&1 | tail -10
```

期望：全部通过

**Step 6: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/ontology/
git add 05-SREmate/src/test/java/com/yycome/sremate/domain/ontology/
git add 05-SREmate/src/test/resources/ontology/
git commit -m "feat(ontology): add EntityRegistry with schema validation and path finding"
```

---

## Phase 2：System Prompt 注入

### Task 4：注入本体摘要到 AgentConfiguration

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/infrastructure/config/AgentConfiguration.java`
- Modify: `05-SREmate/src/main/resources/prompts/sre-agent.md`

**Step 1: 在 sre-agent.md 顶部添加本体占位符**

在 `prompts/sre-agent.md` 文件开头（第一行）加入：

```markdown
## 数据模型（本体）

{{ontology_summary}}

---
```

（注意：用 `{{ontology_summary}}` 作为占位符，避免与 Spring 的 `${}` 语法冲突）

**Step 2: 修改 AgentConfiguration，注入摘要**

```java
// AgentConfiguration.java 修改后
@Configuration
public class AgentConfiguration {

    @Value("classpath:prompts/sre-agent.md")
    private Resource sreAgentPrompt;

    @Bean
    public ChatClient sreAgent(ChatClient.Builder builder,
                               ToolCallbackProvider sreTools,
                               EntityRegistry entityRegistry) throws Exception {
        String promptContent = sreAgentPrompt.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String ontologySummary = entityRegistry.getSummaryForPrompt();
        String finalPrompt = promptContent.replace("{{ontology_summary}}", ontologySummary);

        return builder
                .defaultSystem(finalPrompt)
                .defaultToolCallbacks(sreTools)
                .build();
    }

    // sreTools bean 保持不变
    ...
}
```

**Step 3: 启动应用验证注入成功**

```bash
cd 05-SREmate
mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | grep "EntityRegistry"
```

期望日志：`[EntityRegistry] 本体加载完成：7 个实体，7 条关系`

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/infrastructure/config/AgentConfiguration.java
git add 05-SREmate/src/main/resources/prompts/sre-agent.md
git commit -m "feat(ontology): inject ontology summary into agent system prompt"
```

---

## Phase 3：Contract 工具拆分（PoC 核心）

### Task 5：写失败集成测试

**Files:**
- Create: `05-SREmate/src/test/java/com/yycome/sremate/ContractOntologyIT.java`

**Step 1: 写失败测试（4 个新方法 + 旧方法废弃验证）**

```java
// ContractOntologyIT.java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;

class ContractOntologyIT extends BaseSREIT {

    // ── 新方法：按实体拆分 ────────────────────────────────

    @Test
    void contractBasic_shouldCallQueryContractBasic() {
        ask("C1767173898135504的合同基本信息");
        assertToolCalled("queryContractBasic");
        assertAllToolsSuccess();
    }

    @Test
    void contractNodes_shouldCallQueryContractNodes() {
        ask("C1767173898135504的合同节点");
        assertToolCalled("queryContractNodes");
        assertAllToolsSuccess();
    }

    @Test
    void contractSignedObjects_shouldCallQueryContractSignedObjects() {
        ask("C1767173898135504的签约单据");
        assertToolCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    @Test
    void contractFields_shouldCallQueryContractFields() {
        ask("C1767173898135504的合同字段");
        assertToolCalled("queryContractFields");
        assertAllToolsSuccess();
    }

    // ── 多跳查询：订单 → 合同 → 子实体 ──────────────────

    @Test
    void orderContract_allData_shouldCallContractsByOrderIdThenSubTools() {
        ask("825123110000002753下的合同数据");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractBasic");
        assertAllToolsSuccess();
    }

    @Test
    void orderContract_signedObjects_shouldCallSignedObjectsTool() {
        ask("825123110000002753合同的签约单据");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }
}
```

**Step 2: 运行确认失败**

```bash
mvn test -pl 05-SREmate -Dtest=ContractOntologyIT -q 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

期望：FAIL（方法不存在）

---

### Task 6：在 ContractQueryService 添加单表查询方法

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/domain/contract/service/ContractQueryService.java`

**Step 1: 添加 4 个单表查询方法**（直接调用已有 DAO 方法，无需写 SQL）

在 `ContractQueryService` 末尾添加：

```java
/** 查询合同主表基础数据 */
public Map<String, Object> queryContractBasic(String contractCode) {
    return contractDao.fetchContractBase(contractCode);
}

/** 查询合同节点数据 */
public List<Map<String, Object>> queryContractNodes(String contractCode) {
    return contractDao.fetchNodes(contractCode);
}

/** 查询合同签约单据关联（contract_quotation_relation） */
public List<Map<String, Object>> queryContractSignedObjects(String contractCode) {
    return contractDao.fetchQuotations(contractCode);
}

/** 查询合同字段数据（自动路由分表） */
public Map<String, Object> queryContractFields(String contractCode) {
    return contractDao.fetchFields(contractCode);
}
```

---

### Task 7：在 ContractQueryTool 添加 4 个 @Tool 方法

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java`

**Step 1: 添加 4 个新 @Tool 方法**

在 `ContractQueryTool` 类末尾，`queryContractConfig` 方法之后添加：

```java
@Tool(description = "查询合同主表基础数据。触发词：合同基本信息、合同详情、合同状态。参数：contractCode（C前缀合同号，必填）")
@DataQueryTool
public String queryContractBasic(String contractCode) {
    return ToolExecutionTemplate.execute("queryContractBasic", () -> {
        Map<String, Object> result = contractQueryService.queryContractBasic(contractCode);
        if (result == null) return ToolResult.notFound("合同", contractCode);
        return contractQueryService.toJson(result);
    });
}

@Tool(description = "查询合同节点/流程节点数据。触发词：合同节点、合同流程、节点记录。参数：contractCode（C前缀合同号，必填）")
@DataQueryTool
public String queryContractNodes(String contractCode) {
    return ToolExecutionTemplate.execute("queryContractNodes", () -> {
        List<Map<String, Object>> result = contractQueryService.queryContractNodes(contractCode);
        return contractQueryService.toJson(result);
    });
}

@Tool(description = "查询合同签约的单据对象（contract_quotation_relation），包含报价单和S单的bill_code。触发词：签约单据、合同关联单据、合同签约对象。参数：contractCode（C前缀合同号，必填）")
@DataQueryTool
public String queryContractSignedObjects(String contractCode) {
    return ToolExecutionTemplate.execute("queryContractSignedObjects", () -> {
        List<Map<String, Object>> result = contractQueryService.queryContractSignedObjects(contractCode);
        return contractQueryService.toJson(result);
    });
}

@Tool(description = "查询合同扩展字段数据（contract_field_sharding分表）。触发词：合同字段、合同扩展字段。参数：contractCode（C前缀合同号，必填）")
@DataQueryTool
public String queryContractFields(String contractCode) {
    return ToolExecutionTemplate.execute("queryContractFields", () -> {
        Map<String, Object> result = contractQueryService.queryContractFields(contractCode);
        return contractQueryService.toJson(result);
    });
}
```

**Step 2: 运行集成测试**

```bash
./05-SREmate/scripts/run-integration-tests.sh 2>&1 | grep -E "FAIL|ERROR|Tests run|BUILD"
```

期望：ContractOntologyIT 全部通过，其他测试无回归

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/domain/contract/service/ContractQueryService.java
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java
git add 05-SREmate/src/test/java/com/yycome/sremate/ContractOntologyIT.java
git commit -m "feat(ontology): split queryContractData into 4 entity-aligned tool methods"
```

---

### Task 8：废弃 queryContractData 和 QueryDataType（可选，回归确认后执行）

> ⚠️ 只在 Task 7 的集成测试全部通过后执行

**Files:**
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java`
- Modify: `05-SREmate/src/main/java/com/yycome/sremate/types/enums/QueryDataType.java`

**Step 1: 给 queryContractData 添加 @Deprecated**

```java
/** @deprecated 使用 queryContractBasic / queryContractNodes / queryContractSignedObjects / queryContractFields 替代 */
@Deprecated
@Tool(description = "【已废弃】请使用拆分后的专用工具方法。此方法仅保留用于兼容性过渡。")
public String queryContractData(String contractCode, String dataType) {
    // 保持原有实现不变，仅标记废弃
    ...
}
```

**Step 2: 运行全量集成测试确认无回归**

```bash
./05-SREmate/scripts/run-integration-tests.sh 2>&1 | tail -20
```

期望：BUILD SUCCESS

**Step 3: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/agent/ContractQueryTool.java
git commit -m "refactor(ontology): deprecate queryContractData, replaced by entity-aligned methods"
```

---

## Phase 4：验收标准验证

### Task 9：Golden Set 集成测试

**Files:**
- Create: `05-SREmate/src/test/java/com/yycome/sremate/OntologyGoldenSetIT.java`

**Step 1: 写 Golden Set 测试**

```java
// OntologyGoldenSetIT.java
package com.yycome.sremate;

import org.junit.jupiter.api.Test;

/**
 * 验收标准 AC2：Golden Set 测试
 * 验证本体注入后 agent 工具选择 100% 准确
 */
class OntologyGoldenSetIT extends BaseSREIT {

    // 合同基础数据
    @Test
    void goldenSet_contractBasic() {
        ask("825123110000002753下的合同基本信息");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractBasic");
        assertToolCallOrder("queryContractsByOrderId", "queryContractBasic");
        assertAllToolsSuccess();
    }

    // 合同节点
    @Test
    void goldenSet_contractNodes() {
        ask("825123110000002753下的合同节点");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractNodes");
        assertToolCallOrder("queryContractsByOrderId", "queryContractNodes");
        assertAllToolsSuccess();
    }

    // 合同签约单据（contract domain 的 SubOrder 路径）
    @Test
    void goldenSet_contractSignedObjects() {
        ask("825123110000002753合同的签约单据");
        assertToolCalled("queryContractsByOrderId");
        assertToolCalled("queryContractSignedObjects");
        assertAllToolsSuccess();
    }

    // 报价单（quote domain）
    @Test
    void goldenSet_budgetBill() {
        ask("826031111000001859的报价单");
        assertToolCalled("queryBudgetBillList");
        assertToolNotCalled("queryContractsByOrderId");
        assertAllToolsSuccess();
    }

    // 报价维度 S单（quote domain 多跳）
    @Test
    void goldenSet_subOrderViaQuote() {
        ask("826031111000001859报价单下的S单");
        assertToolCalled("queryBudgetBillList");
        assertToolCalled("querySubOrderInfo");
        assertToolCallOrder("queryBudgetBillList", "querySubOrderInfo");
        assertAllToolsSuccess();
    }
}
```

**Step 2: 运行 Golden Set 测试**

```bash
mvn test -pl 05-SREmate -Dtest=OntologyGoldenSetIT -q 2>&1 | grep -E "PASS|FAIL|Tests run"
```

期望：全部通过（AC2：100% 命中率）

**Step 3: Commit**

```bash
git add 05-SREmate/src/test/java/com/yycome/sremate/OntologyGoldenSetIT.java
git commit -m "test(ontology): add golden set acceptance tests for AC2"
```

---

## Phase 5：可视化页面

### Task 10：本体可视化 API + 页面

**Files:**
- Create: `05-SREmate/src/main/java/com/yycome/sremate/trigger/http/OntologyController.java`
- Create: `05-SREmate/src/main/resources/static/ontology.html`

**Step 1: 创建 OntologyController**

```java
// OntologyController.java
package com.yycome.sremate.trigger.http;

import com.yycome.sremate.domain.ontology.model.OntologyModel;
import com.yycome.sremate.domain.ontology.service.EntityRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
```

**Step 2: 创建 ontology.html**

创建 `src/main/resources/static/ontology.html`，包含：
- 引入 vis-network CDN: `https://unpkg.com/vis-network/standalone/umd/vis-network.min.js`
- `fetch('/api/ontology')` 获取数据
- 将 entities 渲染为节点（颜色：contract domain=蓝色，quote domain=橙色，无 domain=绿色）
- 将 relations 渲染为有向边（label 显示 relation.label 和 via 字段）
- 开启 hierarchical layout（方向：UD，从上往下）
- 节点点击时在右侧 panel 显示该实体的属性列表

关键代码结构：
```javascript
fetch('/api/ontology').then(r => r.json()).then(data => {
    const domainColor = { contract: '#4A90D9', quote: '#F5A623' };

    // 从 relations 推断每个实体的 domain
    const entityDomain = {};
    data.relations.forEach(r => {
        if (r.domain) entityDomain[r.from] = r.domain;
    });

    const nodes = data.entities.map(e => ({
        id: e.name,
        label: e.name + (e.table ? '\n[' + e.table + ']' : ''),
        color: { background: domainColor[entityDomain[e.name]] || '#7ED321' },
        title: (e.attributes || []).map(a => a.name + ': ' + a.type).join('\n')
    }));

    const edges = data.relations.map((r, i) => ({
        id: i,
        from: r.from,
        to: r.to,
        label: r.label + '\nvia: ' + (r.via ? r.via.source_field + '→' + r.via.target_field : ''),
        arrows: 'to',
        color: { color: domainColor[r.domain] || '#999' }
    }));

    const network = new vis.Network(
        document.getElementById('network'),
        { nodes: new vis.DataSet(nodes), edges: new vis.DataSet(edges) },
        { layout: { hierarchical: { direction: 'UD', sortMethod: 'directed' } },
          physics: false }
    );
});
```

**Step 3: 手动验证**

启动应用后访问：`http://localhost:8080/ontology.html`

验收标准（AC5）：
- 页面正常加载，显示所有 7 个实体节点
- 节点颜色区分 domain（蓝=签约，橙=报价，绿=通用）
- 边上显示 relation label 和 via 字段
- 修改 YAML 后重启，页面自动反映最新本体

**Step 4: Commit**

```bash
git add 05-SREmate/src/main/java/com/yycome/sremate/trigger/http/OntologyController.java
git add 05-SREmate/src/main/resources/static/ontology.html
git commit -m "feat(ontology): add ontology visualization API and interactive HTML page"
```

---

## Phase 6：全量回归

### Task 11：运行全部集成测试

**Step 1: 运行所有集成测试**

```bash
./05-SREmate/scripts/run-integration-tests.sh 2>&1 | tail -30
```

期望：BUILD SUCCESS，零失败

**Step 2: 验收标准 checklist**

```
✅ AC1：应用启动成功，EntityRegistry 日志显示 7 个实体
✅ AC2：OntologyGoldenSetIT 全部通过（100% 命中率）
✅ AC3：领域歧义 - 合同域/报价域关键词各自走正确路径（ContractOntologyIT）
✅ AC4：./run-integration-tests.sh BUILD SUCCESS（无回归）
✅ AC5：ontology.html 显示最新本体图，无需手动更新
```

**Step 3: 如果 Golden Set 有用例失败**

分析失败原因：
1. 查看 agent 实际调用了哪些工具（集成测试日志）
2. 对比 `getSummaryForPrompt()` 输出内容（EntityRegistry 日志）
3. 调整 sre-agent.md 中本体摘要的措辞，使描述更贴近用户问法
4. 调整新 @Tool 的 description 触发词

**Step 4: 全量通过后提交最终 Commit**

```bash
git add .
git commit -m "feat(ontology): complete PoC - ontology-driven contract query refactoring

- EntityRegistry: YAML-based ontology with schema validation and path finding
- domain-ontology.yaml: 7 entities, 7 relations across contract/quote domains
- ContractQueryTool: split into 4 entity-aligned @Tool methods
- System prompt: ontology summary injected for improved agent accuracy
- Visualization: /ontology.html with vis.js interactive graph
- Tests: EntityRegistryTest + ContractOntologyIT + OntologyGoldenSetIT (AC2 golden set)"
```

---

## 注意事项

1. **jackson-dataformat-yaml 依赖**：Spring Boot 内置了 SnakeYAML，但 `jackson-dataformat-yaml` 需要确认 pom.xml 中是否已有。如果没有，在 `05-SREmate/pom.xml` 中添加：
   ```xml
   <dependency>
       <groupId>com.fasterxml.jackson.dataformat</groupId>
       <artifactId>jackson-dataformat-yaml</artifactId>
   </dependency>
   ```
   版本由 Spring Boot BOM 管理，无需指定。

2. **ContractQueryService.queryContractNodes 返回类型**：注意 `fetchNodes` 返回 `List<Map<String, Object>>`，而 `queryContractBasic` 返回 `Map<String, Object>`，Tool 方法中的序列化处理需要对应。

3. **`assertToolCallOrder` 方法**：如果 `BaseSREIT` 中没有这个方法，跳过顺序断言，只保留 `assertToolCalled`。

4. **PoC 分支**：在新分支上开发，完成 Golden Set 验收后再合并 master。
