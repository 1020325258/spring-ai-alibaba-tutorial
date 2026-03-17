# SREmate - 智能 SRE 值班助手

基于 Spring AI + Qwen 的智能运维助手，为 SRE 团队提供高效的值班支持。

## 项目亮点

### 1. 本体论驱动架构 - 查询性能提升 10 倍+

传统 Agent 架构：LLM 串行调用多个工具 → 每次调用都需要 LLM 决策 → 耗时 30+ 秒

SREmate 本体论架构：LLM 调用一次 → 引擎自动并行查询 → 耗时 ~3 秒

```
┌─────────────┐     ┌─────────────────────┐     ┌─────────────────────┐
│   用户提问   │ ──▶ │  LLM 识别意图        │ ──▶ │  ontologyQuery()    │
│  "订单xxx"  │     │  entity=Order       │     │  一次调用完成       │
└─────────────┘     └─────────────────────┘     └─────────────────────┘
                                                          │
                        ┌─────────────────────────────────┼─────────────────────────────────┐
                        ▼                                 ▼                                 ▼
                ┌─────────────┐                   ┌─────────────┐                   ┌─────────────┐
                │ 查询合同列表 │                   │ 查询节点数据 │                   │ 查询签约单据 │
                └─────────────┘                   └─────────────┘                   └─────────────┘
                        │                                 │                                 │
                        └─────────────────────────────────┴─────────────────────────────────┘
                                                          ▼
                                                  ┌─────────────┐
                                                  │  并行执行    │  ← CompletableFuture
                                                  │  同时返回    │
                                                  └─────────────┘
```

**核心优势：**
- LLM 只做意图识别，不参与执行优化
- 引擎自动分析数据依赖，并行执行无依赖的查询
- 新增实体只需实现 Gateway + 配置 YAML，无需修改查询引擎

### 2. 极速响应 - 绕过 LLM 二次处理

```
传统模式：用户提问 → LLM 调用工具 → 工具返回数据 → LLM 归纳输出

SREmate：用户提问 → LLM 识别意图 → 工具返回数据 → **直接输出**
```

**实现原理：** 通过 `@DataQueryTool` 注解，数据查询类工具的结果绕过 LLM 归纳，直接流式输出给用户，节省 LLM 处理时间和 Token 消耗。

### 3. 清晰的模型定位 - 仅做意图识别

| 职责 | 执行方 | 说明 |
|------|--------|------|
| 意图识别 | LLM | 理解用户需求，调用 `ontologyQuery` |
| 依赖分析 | 引擎 | 根据本体定义确定查询路径 |
| 并行执行 | 引擎 | 同时查询无依赖的关联数据 |
| 结果输出 | 系统层 | 直接输出，无需 LLM 归纳 |

### 4. 按需查询关联数据

通过 `queryScope` 参数精确控制返回数据范围：

**参数说明：**
- `entity`: 起始实体（根据编号格式判断：纯数字→Order，C开头→Contract）
- `value`: 起始值（订单号或合同号）
- `queryScope`: 目标实体（用户想查什么数据，就传对应实体名）
  - 不传或 `"list"`: 仅返回起始实体本身，不展开关联
  - `"Contract"`: 展开到合同数据
  - `"ContractNode"`: 展开到节点数据
  - 多目标: `"ContractNode,ContractQuotationRelation"`

**示例：**
```java
// "825123110000002753下的合同" → 从订单出发，展开到合同
ontologyQuery(entity="Order", value="825123110000002753", queryScope="Contract")

// "C1767150648920281的节点" → 从合同出发，展开到节点
ontologyQuery(entity="Contract", value="C1767150648920281", queryScope="ContractNode")

// "825123110000002753合同的签约单据和节点" → 从订单出发，展开到多个目标
ontologyQuery(entity="Order", value="825123110000002753", queryScope="ContractNode,ContractQuotationRelation")
```

### 5. 完善的集成测试 - 基于 Claude Code 开发

- 测试验证**工具调用行为**，而非输出内容
- 不受业务数据变化影响，测试稳定可靠
- 提供丰富的断言方法：`assertToolCalled()`、`assertToolNotCalled()`、`assertAllToolsSuccess()`

```java
@Test
void orderContract_allData_shouldCallOntologyQuery() {
    ask("825123110000002753下的合同数据");
    assertToolCalled("ontologyQuery");
    // 禁止调用旧的工具
    assertToolNotCalled("queryContractsByOrderId");
    assertAllToolsSuccess();
}
```

### 6. YAML 驱动的配置

**接口配置：** 新增 HTTP 接口无需修改 Java 代码

```yaml
- id: sign-order-list
  name: 查询可签约子单列表
  urlTemplate: "http://service.${env}.ttb.test.ke.com/api/..."
```

**本体配置：** 定义实体和关系

```yaml
entities:
  - name: Contract
    description: "合同实体"
    attributes:
      - name: contractCode
        type: string
        description: "合同编号"

relations:
  - label: has_nodes
    from: Contract
    to: ContractNode
    via:
      source_field: contractCode
      target_field: contractCode
```

### 7. 分层精炼日志

| 层级 | 内容 | 示例 |
|------|------|------|
| AOP 层 | 入口：工具名 + 参数 | `[TOOL] ontologyQuery(entity=Order, value=xxx)` |
| 工具层 | 结果：耗时 + 摘要 | `[TOOL] ontologyQuery → 133ms, ok` |
| Gateway 层 | 查询详情 | `[ContractGateway] queryByField: contractCode = C1767...` |

### 8. 多环境支持

命令行一键切换测试环境：

```
/env    # 查看当前环境，输入序号切换
```

支持环境：`nrs-escrow`（测试）、`offline-beta`（基准）

---

## 技术栈

- **框架：** Spring AI + Spring Boot 3.x
- **模型：** Qwen-Turbo（通义千问）
- **并行执行：** Java CompletableFuture
- **终端：** JLine（支持 Tab 补全、历史记录）
- **测试：** JUnit 5 + Spring Boot Test

## 快速开始

```bash
# 配置 API Key
export AI_DASHSCOPE_API_KEY=your_api_key_here

# 运行集成测试（需要 Java 21）
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn test -Dtest=ContractOntologyIT

# 启动交互式命令行
cd 05-SREmate && mvn spring-boot:run
```

## 使用指南

```
╔══════════════════════════════════════════════════════════════╗
      智能 SRE 值班助手  ·  v2.0  ·  Powered by Qwen-Turbo
      当前环境: nrs-escrow 测试环境
╚══════════════════════════════════════════════════════════════╝

可用命令：
  /tools  显示数据查询工具
  /help   显示帮助信息
  /stats  查看性能统计
  /trace  查看最近工具调用记录
  /env    查看或切换环境
  /quit   退出程序

[nrs-escrow] 你: 825123110000002753下的合同数据
[助手] 正在查询...
{"queryEntity":"Order","contracts":[...]}  ← 直接输出 JSON，3秒内返回
```

## 项目结构

```
05-SREmate/
├── src/main/
│   ├── java/com/yycome/sremate/
│   │   ├── trigger/agent/   # @Tool 工具类
│   │   │   └── OntologyQueryTool.java  # 统一查询入口
│   │   ├── domain/ontology/ # 本体论领域（核心）
│   │   │   ├── model/       # 本体模型
│   │   │   ├── service/     # 实体注册中心
│   │   │   ├── engine/      # 查询引擎
│   │   │   └── gateway/     # 实体数据网关
│   │   ├── infrastructure/  # 基础设施（注解、模板）
│   │   ├── config/          # Spring 配置
│   │   └── aspect/          # AOP 切面
│   └── resources/
│       ├── endpoints/       # HTTP 接口模板（YAML）
│       ├── ontology/        # 本体论定义（YAML）
│       ├── prompts/         # LLM 系统提示词
│       └── skills/          # SRE 知识库
└── src/test/                # 集成测试
```

详细开发规范见 [CLAUDE.md](./CLAUDE.md)

## 性能对比

| 场景 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| 订单→合同→关联数据 | 34秒（13次串行 LLM 调用） | 2-4秒 | **8-17倍** |
| 合同→关联数据 | 8秒（4次串行 LLM 调用） | 1-2秒 | **4-8倍** |

### 耗时分析（实际测试数据）

```
⏱ 首字节: 1198ms | 工具耗时: 419ms | 总耗时: 1198ms
[DirectOutput] ✓ 已生效，绕过 LLM 处理
```

**关键优化**：DirectOutput 生效后立即终止流，总耗时 = 首字节时间。

- **首字节时间（ttfb）**：1-2.5秒，LLM 意图识别（Qwen API 固有延迟）
- **工具耗时**：100-500ms，本体论引擎并行查询数据库
- **总耗时**：与首字节时间一致，无额外等待

### DirectOutput 机制

SREmate 的核心优化之一：数据查询工具的结果**直接输出**，绕过 LLM 二次处理。

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
- 集成测试也支持此机制，测试结果更准确

## 架构升级历史

### v2.4 - QueryScope 枚举类 (2026-03-16)

#### 新增功能

**QueryScope 枚举类**: 为 `queryScope` 参数提供类型安全的枚举支持。

```java
public enum QueryScope {
    DEFAULT("default", "默认展开"),
    LIST("list", "仅返回起始实体"),
    CONTRACT("Contract", "展开到合同实体"),
    CONTRACT_NODE("ContractNode", "展开到合同节点"),
    CONTRACT_QUOTATION_RELATION("ContractQuotationRelation", "展开到签约单据"),
    // ... 更多实体
}
```

#### API 增强

**OntologyQueryEngine** 新增枚举版本 `query()` 方法：

```java
// 字符串版本（向后兼容）
Map<String, Object> query(String entity, String value, String queryScope);

// 枚举版本（类型安全）
Map<String, Object> query(String entity, String value, QueryScope queryScope);
```

#### 测试覆盖

- QueryScopeTest: 7/7 通过
- OntologyQueryEngineTest: 9/9 通过
- 全量测试: 35/35 通过

---

### v2.3 - 多目标查询优化 (2026-03-15)

#### 新增功能

**多目标查询**: `queryScope` 支持逗号分隔多个目标实体，只查询用户需要的数据。

```java
// 只查节点和签约单据，不查 fields、form、config
ontologyQuery(entity=Order, value=825123110000002753, queryScope=ContractNode,ContractQuotationRelation)
```

#### 性能提升

| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 用户要"签约单据和节点" | 查询所有关联数据（fields、form、config 等） | 只查询 nodes、signedObjects |
| 工具耗时 | 36秒+ | 2-3秒 |

#### 其他优化

- **删除废弃代码**: 移除未使用的 `OntologyQueryPlannerTool`
- **OrderGateway 简化**: Order 作为虚拟实体，只返回 `projectOrderId`，Contract 通过关系展开获取
- **DirectOutput 跨线程支持**: 使用 `requestId + ConcurrentHashMap` 替代 `ThreadLocal`

---

### v2.2 - 本体论引擎数据驱动化改造 (2026-03-15)

#### 改造目标

**达成目标**: 新增实体只需 YAML + Gateway，`OntologyQueryTool` 和 `OntologyQueryEngine` 零改动。

#### 核心变更

1. **新增 `OntologyQueryEngine` 查询引擎**
   - 承接所有查询执行逻辑（图遍历、并行执行、层级组装）
   - 支持 default 展开和 scoped 路径查询
   - 同层并行查询优化（CompletableFuture）

2. **EntityRegistry 增强**
   - 新增 `findRelationPath()` - BFS 查找最短关系链
   - 新增 `getOutgoingRelations()` - 获取实体出边
   - 新增 `getEntity()` - 按名称查找实体

3. **模型扩展**
   - `OntologyEntity` 新增 `displayName`、`aliases`、`lookupStrategies` 字段
   - 新增 `LookupStrategy` 模型，支持多格式查询入口

4. **OntologyQueryTool 精简**
   - 从 330 行精简至 63 行（约 80% 缩减）
   - 核心逻辑委托给 `OntologyQueryEngine`
   - 保留 scope 简写兼容（form/nodes/fields 等）

5. **sre-agent.md 提示词更新**
   - queryScope 参数改为推荐使用实体名
   - 支持 `ContractNode`、`ContractForm`、`ContractQuotationRelation` 等

#### 新增实体 SOP（改造后）

```yaml
# Step 1: YAML 添加实体和关系
entities:
  - name: NewEntity
    displayName: "新实体"
    lookupStrategies:
      - field: contractCode
        pattern: "^C\\d+"

relations:
  - from: Contract
    to: NewEntity
    label: has_new_entities
    via: { source_field: contractCode, target_field: contractCode }
```

```java
// Step 2: 实现 Gateway
@Component
public class NewEntityGateway implements EntityDataGateway {
    @PostConstruct public void init() { registry.register(this); }
    @Override public String getEntityName() { return "NewEntity"; }
    @Override public List<Map<String, Object>> queryByField(String field, Object value) { ... }
}
```

**无需修改 `OntologyQueryTool` 和 `OntologyQueryEngine`！**

#### 测试验证

- EntityRegistryTest: 10/10 通过
- OntologyQueryEngineTest: 5/5 通过
- ContractOntologyIT: 11/11 通过

---

### v2.1 - 本体论驱动并行查询引擎 (2026-03)

#### 新增功能（v2.1.1）

**报价单查询接入本体论架构：**
- 新增 `BudgetBillGateway` 封装 HTTP 接口调用
- 支持通过 `ontologyQuery(entity=BudgetBill, value=订单号)` 查询报价单及子单
- 报价单数据自动聚合子单信息

**使用示例：**
```
用户: 826031111000001859的报价单
助手: {"queryEntity":"BudgetBill","queryValue":"826031111000001859","budgetBills":[...]}
```

#### 升级背景

**原架构痛点：**

1. **工具手动编写，维护困难**
   - 每个查询场景需要手动编写独立的 `@Tool` 方法
   - 例如：`queryContractsByOrderId`、`queryContractNodes`、`queryContractFields` 等
   - 新增查询维度必须新增工具方法，代码膨胀严重

2. **LLM 串行调用，性能瓶颈**
   - 查询订单下的合同及关联数据需要 13 次 LLM 调用
   - 每次调用都需等待 LLM 决策下一步操作
   - 总耗时高达 30+ 秒，用户体验极差

3. **职责混乱，难以扩展**
   - LLM 同时承担意图识别和执行优化职责
   - 工具方法包含业务逻辑和数据组装，耦合度高
   - 新增实体需要修改多处代码，容易遗漏

4. **测试不稳定，回归成本高**
   - 测试依赖 LLM 输出内容，受数据变化影响大
   - 缺乏统一的测试框架，断言逻辑分散

**架构演进方向：**

```
原架构：LLM 意图识别 → 串行调用多个工具 → LLM 归纳输出
                    ↓ 问题：耗时、耦合、难维护

新架构：LLM 意图识别 → 本体论引擎并行查询 → 直接输出
                    ↓ 优势：快速、解耦、易扩展
```

#### 核心变更

1. **新增 `ontologyQuery` 统一查询入口**
   - 替代原有的串行工具调用模式
   - LLM 只需调用一次，引擎自动并行查询关联数据

2. **DirectOutput 机制优化**
   - 数据查询结果直接输出，绕过 LLM 二次处理
   - 使用 `subscribe()` + `dispose()` 实现流式即时终止
   - 总耗时 = 首字节时间，无额外等待

3. **实体数据网关（Gateway）模式**
   - 每个实体实现 `EntityDataGateway` 接口
   - 新增实体只需：Gateway 实现 + YAML 配置

4. **集成测试框架增强**
   - `BaseSREIT` 支持 DirectOutput 旁路
   - 耗时分析：首字节、工具耗时、总耗时分离
   - 测试报告自动生成到 `docs/test-execution-report.md`

**性能提升：**

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| 订单→合同→关联数据 | 34秒 | 2-4秒 |
| LLM 调用次数 | 13次串行 | 1次 |
| 数据输出方式 | LLM 归纳 | 直接输出 |

**新增文件：**
- `domain/ontology/engine/` - 查询引擎核心组件
- `domain/ontology/gateway/` - 实体数据网关实现（含 BudgetBillGateway）
- `trigger/agent/OntologyQueryTool.java` - 统一查询入口
- `docs/plans/2026-03-14-ontology-query-engine-design.md` - 架构设计文档

#### v2.1.1 - 报价单查询接入（2026-03）

**新增功能：**
- 新增 `BudgetBillGateway` 封装 HTTP 接口调用
- 支持通过 `entity=BudgetBill` 查询报价单及子单
- 报价单数据自动聚合子单信息

**使用示例：**
```
用户: 826031111000001859的报价单
助手: {"queryEntity":"BudgetBill","queryValue":"826031111000001859","budgetBills":[{"billCode":"GBILL...","subOrders":[...]}]}
```

---

## 许可证

MIT License
