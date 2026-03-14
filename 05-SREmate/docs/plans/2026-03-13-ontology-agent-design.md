# 本体论驱动的 Agent 查询能力设计

**日期**：2026-03-13
**状态**：设计确认，待实现

---

## 一、问题背景

### 现状痛点

1. **新增查询能力成本高**：每增加一种查询，需要手写 SQL、接口、@Tool 方法
2. **工具膨胀导致 agent 选错工具**：工具列表越来越长，description 靠关键词匹配，歧义增加
3. **数据模型关系不清晰**：同一实体（如合同）被多个 Tool 方法从不同角度封装，关联关系散落在 Java 代码里
4. **复合查询不透明**：`queryBudgetBillList` 内部聚合了子单查询，底层调了几张表、怎么关联，对外完全不可见

### 典型反例

```java
// queryContractData 用一个枚举模拟多实体查询，是反模式
queryContractData(contractCode, QueryDataType.ALL)
queryContractData(contractCode, QueryDataType.CONTRACT_NODE)
queryContractData(contractCode, QueryDataType.CONTRACT_FIELD)
```

---

## 二、解决方案：实体注册表（方案 B）

### 核心思路

用一份 **本体 YAML 文件**统一建模数据实体、属性、关联关系和查询能力，作为整个系统的唯一数据模型 source of truth。

```
domain-ontology.yaml          ← 唯一数据模型 source of truth
       ↓ 启动加载
EntityRegistry
  ├── getSummaryForPrompt()   → 注入 system prompt，agent 查询更准确
  ├── findPaths(from, to)     → 多跳路径规划
  └── getOntology()           → /api/ontology 接口，供可视化页面消费
```

### 优先目标

**查询更准确**：agent 通过本体摘要理解数据模型的关联关系，不再靠关键词猜工具。
次要目标：数据模型对开发者可视化透明、新增查询能力更容易。

---

## 三、本体 Schema 设计

文件路径：`src/main/resources/ontology/domain-ontology.yaml`

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

  - name: ContractQuotationRelation
    description: "合同签约的单据对象，存储合同关联的报价单和S单"
    table: contract_quotation_relation
    attributes:
      - { name: contractCode, type: string }
      - { name: bill_code,    type: string, description: "签约单据编号，可为报价单或S单" }

  - name: ContractField
    description: "合同字段数据（分表，按contractCode取模分10张）"
    table: contract_field_sharding
    attributes:
      - { name: contractCode, type: string }

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
  # Order 层
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

  # Contract 内部子表
  - from: Contract
    to: ContractNode
    label: has_nodes
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractQuotationRelation
    label: has_signed_objects
    domain: contract
    description: "合同签约的单据对象，bill_code 可指向报价单或S单"
    via: { source_field: contractCode, target_field: contractCode }

  - from: Contract
    to: ContractField
    label: has_fields
    domain: contract
    via: { source_field: contractCode, target_field: contractCode }

  # BudgetBill → SubOrder
  - from: BudgetBill
    to: SubOrder
    label: splits_into
    domain: quote
    description: "全量数据，报价单拆分形成S单"
    via: { source_field: billCode, target_field: quotationOrderNo }

  # Contract → SubOrder（通过 ContractQuotationRelation 中转）
  - from: ContractQuotationRelation
    to: SubOrder
    label: references_sub_order
    domain: contract
    description: "签约领域关联数据"
    via: { source_field: bill_code, target_field: orderNo }
```

### 关系图

```
Order (projectOrderId)
  ├── has_contracts ──────────────► Contract
  │                                    ├── has_nodes ──────────────► ContractNode
  │                                    ├── has_signed_objects ──────► ContractQuotationRelation
  │                                    │                                  └── references_sub_order ──► SubOrder
  │                                    └── has_fields ─────────────► ContractField
  └── has_budget_bills ────────────► BudgetBill
                                         └── splits_into【全量】 ───► SubOrder
```

### 两个领域的 SubOrder 路径

| 用户意图 | domain | 路径 | 关联键 |
|---------|--------|------|--------|
| 报价/工程维度的S单 | quote | Order → BudgetBill → SubOrder | billCode → quotationOrderNo |
| 签约/合同维度的S单 | contract | Order → Contract → ContractQuotationRelation → SubOrder | contractCode → bill_code → orderNo |

---

## 四、EntityRegistry 设计

```java
@Component
public class EntityRegistry {

    private OntologyModel ontology;

    @PostConstruct
    void load() {
        // 读取 domain-ontology.yaml，反序列化为 OntologyModel
        // 同时执行 Schema 自洽性校验（见验收标准 AC1）
    }

    /** 为 system prompt 提供精简摘要 */
    public String getSummaryForPrompt() { ... }

    /** 路径规划：返回从 from 到 to 的所有可达路径 */
    public List<OntologyPath> findPaths(String from, String to) { ... }

    /** 提供给可视化 API */
    public OntologyModel getOntology() { ... }
}
```

`getSummaryForPrompt()` 输出示例（注入到 system prompt）：

```
【数据模型】
- Order → Contract（签约领域，via projectOrderId）
  - Contract → ContractNode（合同节点，via contractCode）
  - Contract → ContractQuotationRelation（签约单据，via contractCode）
  - Contract → ContractField（合同字段，via contractCode）
- Order → BudgetBill（报价领域，via projectOrderId）
  - BudgetBill → SubOrder【全量】（报价领域，via billCode → quotationOrderNo）

【查询能力】
- 合同编号/C前缀 → queryContractBasic
- 合同节点 → queryContractNodes
- 合同签约单据 → queryContractSignedObjects
- 合同字段 → queryContractFields
- 报价单/报价 → queryBudgetBillList
```

---

## 五、agent 准确性提升机制

### 注入位置

`prompts/sre-agent.md` 新增占位符：

```markdown
## 数据模型（本体）
${ontology.summary}

## 可用工具
...
```

`AgentConfiguration` 构建 ChatClient 时填入 `entityRegistry.getSummaryForPrompt()`。

### 工具 description 精简原则

本体注入后，`@Tool(description=...)` 只描述**触发条件和参数格式**，数据关系由本体承担：

```java
// 之前（冗长，关系描述硬编码）
@Tool(description = "【合同查询】...注意：纯数字是订单号...报价单≠子单...")

// 之后（精简，关系由本体表达）
@Tool(description = "查询合同主表数据。触发词：合同详情、合同信息。参数：contractCode（C前缀）")
```

---

## 六、PoC：合同查询能力改造

### 改造目标

拆分 `queryContractData(contractCode, QueryDataType)` 为四个独立方法，每个方法对应一个本体实体：

| 旧方法 | 新方法 | 对应本体实体 |
|--------|--------|------------|
| queryContractData(code, ALL) | queryContractBasic(contractCode) | Contract |
| queryContractData(code, CONTRACT_NODE) | queryContractNodes(contractCode) | ContractNode |
| —（新增）| queryContractSignedObjects(contractCode) | ContractQuotationRelation |
| queryContractData(code, CONTRACT_FIELD) | queryContractFields(contractCode) | ContractField |

### 用户问"订单下的合同数据"时 agent 的行为

```
用户："825123110000002753下的合同数据"
  ↓ 查本体：Order → Contract（via projectOrderId）
  → 调 queryContractsByOrderId(825123110000002753)
  → 得到 [contractCode1, contractCode2, ...]
  ↓ 查本体：Contract 有 4 个子实体
  → 分别调用：
      queryContractBasic(contractCode)
      queryContractNodes(contractCode)
      queryContractSignedObjects(contractCode)
      queryContractFields(contractCode)
  → 各自返回结果，agent 汇总后回答用户
```

**注意**：当用户问"全部数据"时，agent 主动调用所有 4 个方法，无需单独的聚合工具。

---

## 七、验收标准

### AC1：本体 Schema 自洽性（零容忍）

| 标准 | 验收方式 |
|------|---------|
| YAML 加载不报错 | 应用启动成功 |
| 所有 relation 的 from/to 指向已定义 entity | 启动自检通过 |
| via 字段引用的属性在 entity 中存在 | 启动自检通过 |
| 新增 entity/relation 后应用仍能正常启动 | 回归测试通过 |

### AC2：Agent 多跳查询准确性（Golden Set）

预定义标准查询集，验证工具调用行为：

| 查询 | 期望工具调用 | 期望顺序 |
|------|------------|---------|
| "825123110000002753的合同数据" | queryContractsByOrderId → queryContractBasic | 有序 |
| "825123110000002753的合同节点" | queryContractsByOrderId → queryContractNodes | 有序 |
| "825123110000002753合同的签约单据" | queryContractsByOrderId → queryContractSignedObjects | 有序 |
| "826031报价维度的S单" | queryBudgetBillList → querySubOrderInfo | 有序 |
| "826031合同签约的S单" | queryContractsByOrderId → queryContractSignedObjects | 有序 |

**命中率目标**：Golden Set 100% 通过（PoC 阶段，用例数量小，要求严格）。

### AC3：领域歧义处理

| 场景 | 验收标准 |
|------|---------|
| 含明确领域关键词（"报价"/"合同签约"） | agent 直接走对应路径，不追问 |
| 模糊查询（只说"S单"，无领域词） | agent 反问用户确认领域，不乱猜工具 |

### AC4：无回归

| 标准 | 验收方式 |
|------|---------|
| 所有现有集成测试通过 | `run-integration-tests.sh` 全绿 |
| 本体注入不影响现有工具行为 | 现有 IT 用例无需修改 |

### AC5：开发者体验

| 标准 | 验收方式 |
|------|---------|
| 新增实体只改 YAML，不改 Java（EntityRegistry 以外） | Code Review 验证 |
| 可视化页面反映最新本体，刷新即更新 | 手动验证 |

---

## 八、可视化页面

- 入口：`http://localhost:8080/ontology.html`
- 后端：`GET /api/ontology` 返回本体 JSON
- 前端：`src/main/resources/static/ontology.html`，使用 vis.js 渲染交互式关系图
  - 节点 = 实体（点击展开属性列表）
  - 边 = 关系（显示 label、domain、via 字段）
  - 颜色区分 domain（蓝色=签约领域，橙色=报价领域）

---

## 九、新增查询能力 SOP（PoC 验证后形成）

1. **在 YAML 中声明实体**：添加 entity 定义（name、table、attributes）
2. **声明关联关系**：添加 relation（from、to、label、domain、via）
3. **新增 @Tool 方法**：在对应工具类中添加，description 只写触发词和参数
4. **更新集成测试**：覆盖意图识别、关键词触发、互斥验证三类用例
5. **验证可视化页面**：刷新 `/ontology.html` 确认新实体出现在图中
