# 本体论查询引擎数据驱动化改造设计

**日期**：2026-03-15
**状态**：已审核通过
**目标**：新增实体只需 YAML + Gateway，`OntologyQueryTool` 零改动

---

## 背景与问题

当前 `OntologyQueryTool`（330 行）存在两处硬编码，导致每新增一个实体都必须修改 Java 代码：

1. **实体入口分发**：`executeQuery` 中有 `if ("Order".equals(...))` / `if ("Contract".equals(...))` 等硬判断
2. **关系枚举**：`queryContractWithRelations` 中有 `if (relationsToQuery.contains("has_nodes"))` 等硬判断

**目标**：引擎完全由 YAML 关系图驱动，新增实体的唯一代价是：
- 在 `domain-ontology.yaml` 添加实体和关系定义
- 实现对应的 `EntityDataGateway`

---

## 整体架构

```
现在：
OntologyQueryTool（330行，含全部业务逻辑）

改造后：
OntologyQueryTool        ← 薄层（~40行），只做参数校验和序列化
    └── OntologyQueryEngine  ← 核心算法（新增）
          ├── RelationGraph       ← 关系图（新增，启动时从 YAML 构建）
          ├── EntityRegistry      ← 已有，扩展模型字段
          └── EntityGatewayRegistry ← 已有，不变
```

---

## Section 1：YAML 结构变更

### 1.1 Entity 新增字段

```yaml
entities:
  - name: Contract
    displayName: "合同"                          # 新增：中文显示名，注入 system prompt
    aliases: ["合同数据", "合同信息"]              # 新增：LLM 理解用的中文别名
    description: "合同实体，C前缀编号"
    defaultDepth: 2
    lookupStrategies:                            # 新增：替换原 lookupField
      - field: contractCode
        pattern: "^C\\d+"                        # C1767... → 按合同号查单份合同
      - field: projectOrderId
        pattern: "^\\d{15,}$"                    # 825123... → 按订单号查该订单下所有合同

  - name: Order
    displayName: "订单"
    lookupStrategies:
      - field: projectOrderId
        pattern: "^\\d{15,}$"
    defaultDepth: 2

  - name: ContractQuotationRelation
    displayName: "签约单据"
    aliases: ["合同签约对象", "关联单据"]
    defaultDepth: 0
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"
```

**`lookupStrategies` 语义**：引擎按顺序匹配 pattern，第一个命中的 strategy 决定传给 Gateway 的 `fieldName`。

### 1.2 Relation 不变，resultKey 自动推导

relation 定义保持不变，结果 key 由 `label` 自动推导：

| label | resultKey |
|---|---|
| `has_contracts` | `contracts` |
| `has_signed_objects` | `signedObjects` |
| `has_budget_bills` | `budgetBills` |
| `splits_into` | `subOrders` |

规则：去掉 `has_` 前缀，snake_case 转 camelCase。

---

## Section 2：核心查询算法

### 2.1 两条执行路径

```
ontologyQuery(entity, value, queryScope)
        │
        ├── queryScope=null/default ──→ executeDefault(entity, value, defaultDepth)
        │
        └── queryScope="ContractNode" ──→ path = bfsPath(entity → ContractNode)
                                           executeScoped(entity, value, path)
```

### 2.2 路径查找（BFS）

启动时构建邻接表，查询时 BFS：

```
bfsPath("Order", "ContractQuotationRelation"):

  frontier: [Order]
  pathMap:  {Order: []}

  扩展 Order 出边: has_contracts→Contract, has_budget_bills→BudgetBill
    pathMap[Contract]    = [has_contracts]
    pathMap[BudgetBill]  = [has_budget_bills]

  扩展 Contract 出边: has_signed_objects→ContractQuotationRelation, ...
    命中目标！返回: [has_contracts, has_signed_objects]
```

### 2.3 Scoped 查询执行

`via` 字段驱动每一跳的字段传递：
- `via.source_field`：从父记录提取的字段（作为子查询的值）
- `via.target_field`：传给子 Gateway 的 fieldName

```
executeScoped(Order, "825...", path=[has_contracts, has_signed_objects]):

  Step 1: matchStrategy(Order, "825...") → field=projectOrderId
          OrderGateway.queryByField("projectOrderId", "825...")
          → [{contractCode:"C1",...}, {contractCode:"C2",...}]

  Step 2: 同层并行，对每个 contract：
          source_field=contractCode → record["contractCode"] = "C1"
          ContractQuotationRelationGateway.queryByField("contractCode", "C1")
          contract["signedObjects"] = [...]

  返回层级结构:
  {
    "queryEntity": "Order",
    "queryValue": "825...",
    "contracts": [
      { "contractCode": "C1", "signedObjects": [...] },
      { "contractCode": "C2", "signedObjects": [...] }
    ]
  }
```

### 2.4 Default 查询执行

从起始实体按 `defaultDepth` 递归展开所有出边，同层并行：

```
executeDefault(Order, "825...", remainingDepth=2):

  records = OrderGateway.queryByField("projectOrderId", "825...")

  并行展开所有出边（remainingDepth=2 > 0）:
  ├── has_contracts → 对每条 order record，查 ContractGateway
  │     → 每个 contract 继续 executeDefault(Contract, ..., depth=1)
  │           ├── has_nodes → ContractNodeGateway（depth=0，停止）
  │           ├── has_fields → ContractFieldGateway（depth=0，停止）
  │           └── has_signed_objects → ContractQuotationRelationGateway（depth=0，停止）
  └── has_budget_bills → BudgetBillGateway
        → executeDefault(BudgetBill, ..., depth=1)
              └── splits_into → SubOrderGateway（depth=0，停止）
```

---

## Section 3：组件职责

### 3.1 新增：RelationGraph

```java
@Component
public class RelationGraph {
    // 启动时构建：key=entityName, value=从该实体出发的所有 relation
    private Map<String, List<OntologyRelation>> adjacencyList;

    @PostConstruct
    public void build(EntityRegistry registry) { ... }

    /** BFS 找最短路径，返回 relation 链；找不到返回 null */
    public List<OntologyRelation> findPath(String from, String to) { ... }

    /** 获取某实体的所有出边 */
    public List<OntologyRelation> getOutgoing(String entityName) { ... }
}
```

### 3.2 新增：OntologyQueryEngine

```java
@Component
public class OntologyQueryEngine {

    /** 对外唯一入口 */
    public Map<String, Object> query(String entityName, String value, String queryScope);

    /** 按 defaultDepth 递归展开 */
    private List<Map<String, Object>> expandDefault(
        String entityName, List<Map<String, Object>> records, int depth);

    /** 沿 path 递归挂载子结果 */
    private void attachPathResults(
        List<Map<String, Object>> records, List<OntologyRelation> path, int hop);

    /** 从 label 推导 resultKey */
    private String deriveKey(String label);

    /** 匹配 lookupStrategy */
    private LookupStrategy matchStrategy(OntologyEntity entity, String value);
}
```

### 3.3 改造：OntologyQueryTool

原 330 行 → 改造后约 40 行，仅保留 `@Tool` 声明和序列化：

```java
@Tool(description = """
    【本体论智能查询】根据实体和值自动查询关联数据。
    - entity: 实体类型（Order / Contract / BudgetBill）
    - value: 标识值（订单号或合同号）
    - queryScope: 目标实体名（ContractNode / ContractForm / ContractQuotationRelation 等）
      留空则按默认深度展开全部关联
    """)
@DataQueryTool
public String ontologyQuery(String entity, String value, String queryScope) {
    return ToolExecutionTemplate.execute("ontologyQuery", () -> {
        Map<String, Object> result = engine.query(entity, value, queryScope);
        if (result == null) return ToolResult.notFound(entity, value);
        return objectMapper.writeValueAsString(result);
    });
}
```

### 3.4 改造：OntologyEntity 模型

```java
@Data
public class OntologyEntity {
    private String name;
    private String displayName;                    // 新增
    private List<String> aliases;                  // 新增
    private String description;
    private int defaultDepth;
    private List<LookupStrategy> lookupStrategies; // 替换原 lookupField
    private List<OntologyAttribute> attributes;
}

@Data
public class LookupStrategy {
    private String field;    // gateway queryByField 的 fieldName
    private String pattern;  // value 匹配正则
}
```

### 3.5 不需要改动的部分

| 组件 | 原因 |
|---|---|
| `EntityDataGateway` 接口 | 通用 `queryByField(field, value)`，无需改 |
| `EntityGatewayRegistry` | 不变 |
| 集成测试框架（BaseSREIT） | 不变 |
| 所有具体 Gateway（ContractGateway 等） | 仅需补充新 fieldName 的查询分支 |

---

## Section 4：错误处理

| 错误场景 | 处理方式 |
|---|---|
| value 格式不匹配任何 pattern | `{"error":"无法识别的 value 格式: xxx，支持格式: [^C\\d+, ^\\d{15,}$]"}` |
| queryScope 指定的实体不存在 | `{"error":"未知实体: ContractXxx，可用实体: [Contract, Order, ...]"}` |
| 起始实体到目标无路径 | `{"error":"从 Order 无法直接查询 ContractForm，可通过 entity=Contract 查询"}` |
| 起始实体 Gateway 返回空 | `ToolResult.notFound(entity, value)` |
| 并行子任务 Gateway 异常 | 局部标记 `{"error":"..."}` 不中断整体 |

路径不可达的提示从关系图自动推导，不硬编码。

---

## Section 5：测试策略

### 5.1 单元测试（无 LLM、无 DB）

**RelationGraph：**
```java
findPath("Order", "ContractQuotationRelation") == [has_contracts, has_signed_objects]
findPath("BudgetBill", "ContractNode") == null   // 不可达
findPath("Contract", "ContractForm") == [has_form]
```

**OntologyQueryEngine（mock Gateway）：**
```java
// default 展开深度正确
// scoped 层级结构正确
// 多条父记录时子查询全部触发（并行验证）
// 单 Gateway 失败时其他数据正常返回
```

**LookupStrategy 匹配：**
```java
matchStrategy(Contract, "C1767...").field == "contractCode"    ✓
matchStrategy(Contract, "825123...").field == "projectOrderId" ✓
matchStrategy(Contract, "INVALID") → throws exception          ✓
```

### 5.2 集成测试

现有 `ContractOntologyIT`（11 个测试）直接复用，无需改写。

新增实体后在 `ContractOntologyIT` 追加用例：
```java
@Test
void newEntity_shouldCallOntologyQuery() {
    ask("825123110000002753的新实体数据");
    assertToolCalled("ontologyQuery");
    assertAllToolsSuccess();
}
```

### 5.3 验收基线

```bash
mvn test -Dtest="ContractOntologyIT"   # 必须 11/11 通过
```

---

## 新增实体 SOP（改造后）

改造完成后，新增一个实体只需两步：

**Step 1：YAML 添加实体和关系**
```yaml
entities:
  - name: NewEntity
    displayName: "新实体"
    aliases: ["别名"]
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
    @Override public List<Map<String, Object>> queryByField(String field, Object value) { ... }
}
```

`OntologyQueryTool` 无需任何修改。
