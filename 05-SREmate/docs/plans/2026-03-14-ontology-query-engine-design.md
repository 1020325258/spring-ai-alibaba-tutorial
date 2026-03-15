# 本体论驱动并行查询引擎设计

## 1. 问题背景

### 当前问题
- 每个查询场景需要手动编写聚合代码（如 `queryContractsByOrderId`）
- 新增实体/关系时，需要新增工具方法，维护成本高
- LLM 串行调用原子工具导致性能差（34秒 → 应该只需2秒）

### 目标
- **原子工具层**：只提供单表查询能力
- **本体论引擎**：自动分析依赖关系，并行执行查询
- **LLM 层**：只负责意图识别，不参与执行优化

## 2. 核心概念

### 本体论关系图
```
Order ──has_contracts──→ Contract ──has_nodes──→ ContractNode
                      │                ├──has_fields──→ ContractField
                      │                └──has_signed_objects──→ ContractQuotationRelation
                      │
                      └──has_budget_bills──→ BudgetBill ──splits_into──→ SubOrder
```

### 查询执行模式
```
串行依赖：Order → Contract（需要先拿到 contractCode）
并行查询：Contract → Node/Field/SignedObject（关联键相同，可并行）
```

## 3. 架构设计

### 3.1 整体架构
```
┌─────────────────────────────────────────────────────────────────┐
│                        用户问题                                  │
│              "825123110000002753下的合同数据"                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      LLM 意图识别                                │
│  输入: 用户问题 + 本体定义                                       │
│  输出: 调用 ontologyQuery(entity=Order, value=xxx)              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  OntologyQueryTool（统一入口）                   │
│  职责:                                                          │
│  1. 根据 entity 类型确定查询路径                                 │
│  2. 使用 defaultDepth 控制查询深度                               │
│  3. 并行执行同层级的关联查询                                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                  EntityDataGateway（数据网关）                   │
│  - OrderGateway: 根据 projectOrderId 查询 Contract 列表          │
│  - ContractGateway: 根据 contractCode 查询合同基本信息           │
│  - ContractNodeGateway: 根据 contractCode 查询节点              │
│  - ContractFieldGateway: 根据 contractCode 查询字段             │
│  - ContractQuotationRelationGateway: 根据 contractCode 查询签约单据 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### OntologyQueryTool
统一查询入口，LLM 只需调用此工具：

```java
@Tool(description = """
    【本体论智能查询】根据起始实体和值，自动查询关联数据。

    参数：
    - entity: 起始实体类型（Order/Contract）
    - value: 起始值（订单号或合同号）
    - queryScope: 查询范围（可选）
      - "default": 使用实体默认深度（推荐）
      - "list": 仅查询列表，不查关联
      - "nodes": 仅查节点关系
      - "fields": 仅查字段关系
      - "signed_objects": 仅查签约单据关系
    """)
public String ontologyQuery(String entity, String value, String queryScope) {
    // 自动分析依赖并并行执行查询
}
```

#### EntityDataGateway 接口
每个实体实现一个网关：

```java
public interface EntityDataGateway {
    String getEntityName();
    List<Map<String, Object>> queryByField(String fieldName, Object value);
}
```

#### 实体默认深度配置
在 `domain-ontology.yaml` 中配置：

```yaml
entities:
  - name: Order
    description: "项目订单，纯数字编号"
    defaultDepth: 2    # 默认查询两层：Order → Contract → 关联数据
    attributes: ...

  - name: Contract
    defaultDepth: 2    # 默认查询两层：Contract → Node/Field/SignedObject
    attributes: ...

  - name: ContractNode
    defaultDepth: 0    # 叶子节点，不再继续查询
    attributes: ...
```

### 3.3 执行示例

**输入**：`825123110000002753下的合同数据`

**执行过程**：
```
Step 1: LLM 识别意图 → entity=Order, value=825123110000002753

Step 2: OntologyQueryTool 执行
  2.1 查询订单下的所有合同（OrderGateway）
      → 返回 3 个 Contract: [C1767150648920281, C1767150651718033, C1767150652428677]

  2.2 并行查询每个合同的关联数据（CompletableFuture）:
      - ContractNodeGateway.queryByField("contractCode", "C1767150648920281")
      - ContractFieldGateway.queryByField("contractCode", "C1767150648920281")
      - ContractQuotationRelationGateway.queryByField("contractCode", "C1767150648920281")
      （同时执行，无需等待）

Step 3: 组装结果并返回
```

**返回结果**：
```json
{
  "queryEntity": "Order",
  "queryValue": "825123110000002753",
  "contracts": [
    {
      "contractCode": "C1767150648920281",
      "type": 3,
      "status": 8,
      "nodes": [...],
      "fields": {...},
      "signedObjects": [...]
    },
    ...
  ]
}
```

## 4. 代码结构

### 4.1 核心类
```
domain/ontology/
├── model/
│   ├── OntologyEntity.java        # 实体模型，包含 defaultDepth
│   ├── OntologyRelation.java      # 关系模型
│   └── OntologyModel.java         # 本体模型
├── service/
│   └── EntityRegistry.java        # 实体注册中心
├── engine/
│   ├── EntityDataGateway.java     # 实体数据网关接口
│   ├── EntityGatewayRegistry.java # 网关注册中心
│   ├── QueryStage.java            # 查询阶段（未使用，保留扩展）
│   ├── QueryTask.java             # 查询任务（未使用，保留扩展）
│   ├── QueryPlanGenerator.java    # 查询计划生成器（未使用，保留扩展）
│   └── QueryExecutor.java         # 查询执行器（未使用，保留扩展）
└── gateway/
    ├── OrderGateway.java          # Order 实体网关
    ├── ContractGateway.java       # Contract 实体网关
    ├── ContractNodeGateway.java   # ContractNode 实体网关
    ├── ContractFieldGateway.java  # ContractField 实体网关
    └── ContractQuotationRelationGateway.java  # 签约单据网关

trigger/agent/
└── OntologyQueryTool.java         # 统一查询入口
```

### 4.2 实际实现说明

当前实现采用了**简化版**方案：
- `OntologyQueryTool` 直接实现了并行查询逻辑
- 使用 `CompletableFuture` 实现并行执行
- `QueryPlanGenerator`、`QueryExecutor` 等组件保留但未使用，为未来扩展预留

## 5. 性能对比

| 场景 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 订单→合同→关联数据 | 34秒（13次串行 LLM 调用） | 4-7秒 | **5-8倍** |
| 合同→关联数据 | 8秒（4次串行 LLM 调用） | 2-3秒 | **3-4倍** |

### 实际测试数据

```
⏱ 首字节: 1645ms | 工具耗时: 414ms | 总耗时: 2817ms
[DirectOutput] ✓ 已生效，绕过 LLM 处理
```

**耗时分解**：
- **首字节时间（ttfb）**：1-2.5秒，LLM 意图识别 + 工具调用决策（Qwen API 固有延迟）
- **工具耗时**：100-400ms，本体论引擎并行查询数据库
- **数据序列化**：JSON 序列化输出（大数据量时较慢）

### DirectOutput 机制

核心优化：数据查询工具的结果**直接输出**，绕过 LLM 二次处理。

```java
@Tool(description = "...")
@DataQueryTool  // 标记后，结果直接输出
public String ontologyQuery(String entity, String value, String queryScope) {
    // 工具执行后，结果存入 DirectOutputHolder
    // LLM 首字节到达时，直接返回结果，跳过 LLM 归纳
}
```

**效果**：
- 避免让 LLM 处理大量 JSON 数据
- 减少响应时间和 Token 消耗
- 集成测试也支持此机制

### 实际测试数据

```
⏱ 首字节: 1557ms | 工具耗时: 433ms | 总耗时: 4496ms | 工具: ontologyQuery(433ms)
[DirectOutput] ✓ 已生效，绕过 LLM 处理
```

**耗时分解**：
- **首字节时间（ttfb）**：1-2.5秒，LLM 意图识别 + 工具调用决策（Qwen API 固有延迟）
- **工具耗时**：100-450ms，本体论引擎并行查询数据库
- **总耗时**：首字节 + 数据序列化 + JSON 输出

### DirectOutput 机制

核心优化：数据查询工具的结果**直接输出**，绕过 LLM 二次处理。

```java
@Tool(description = "...")
@DataQueryTool  // 标记后，结果直接输出
public String ontologyQuery(String entity, String value, String queryScope) {
    // 工具执行后，结果存入 DirectOutputHolder
    // LLM 首字节到达时，直接返回结果，跳过 LLM 归纳
}
```

**效果**：
- 避免让 LLM 处理大量 JSON 数据
- 减少响应时间和 Token 消耗
- 集成测试也支持此机制

### 性能优化关键
1. **减少 LLM 调用次数**：从 13 次降到 1 次
2. **并行查询**：同层级关联数据使用 `CompletableFuture` 并行执行
3. **直接输出**：`@DataQueryTool` 注解让结果绕过 LLM 二次处理

## 6. 扩展性

### 新增实体
只需：
1. 在 `domain-ontology.yaml` 添加实体定义（含 `defaultDepth`）
2. 实现 `EntityDataGateway` 接口
3. 在 `OntologyQueryTool` 中添加对应的查询逻辑（可选）

### 新增关系
在 YAML 中添加关系定义，引擎可根据关系自动规划查询路径。

## 7. 测试验证

### 集成测试
```java
@Test
void orderContract_allData_shouldCallOntologyQuery() {
    ask("825123110000002753下的合同数据");
    assertToolCalled("ontologyQuery");
    // 禁止调用旧的工具
    assertToolNotCalled("queryContractsByOrderId");
    assertToolNotCalled("queryContractNodes");
    assertToolNotCalled("queryContractFields");
    assertToolNotCalled("queryContractSignedObjects");
    assertAllToolsSuccess();
}
```

### 测试结果
```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 8. 后续优化方向

1. **自动路由**：基于关系定义自动生成查询路径，无需在 `OntologyQueryTool` 中硬编码
2. **查询缓存**：对频繁查询的实体数据添加缓存
3. **更丰富的 queryScope**：支持更细粒度的查询范围控制
4. **监控指标**：添加查询耗时、并行度等监控指标
