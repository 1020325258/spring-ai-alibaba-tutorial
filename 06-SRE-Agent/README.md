# SREAgent - 智能 SRE 值班助手

> 基于 Spring AI Alibaba 的智能运维助手，为 SRE 团队提供高效的值班支持。

## 项目简介

SREAgent 源自 BSU 签约业务痛点：签约单据关联关系复杂，验证数据需编写多表 JOIN 的 SQL，耗时且易出错。SREAgent 基于"意图识别 + 本体论驱动查询"，实现自然语言查询业务数据，查询耗时从 34s 降至 ~3s，开发过程完全基于 ClaudeCode + OpenSpec。

## 核心亮点

### 1. 本体论驱动的多跳查询引擎

传统 Agent 架构：LLM 串行调用多个工具 → 每次调用都需要 LLM 决策 → 耗时 30+ 秒

SREAgent 本体论架构：LLM 调用一次 → 引擎自动并行查询 → 耗时 ~3 秒

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
                        └─────────────────────────────────┼─────────────────────────────────┘
                                                          ▼
                                                  ┌─────────────┐
                                                  │  并行执行    │
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
- `value`: 起始值（订单号/合同号）
- `queryScope`: 目标实体（用户想查什么数据，就传对应实体名）
  - 不传或 `"list"`: 仅返回起始实体本身，不展开关联
  - `"Contract"`: 展开到合同数据
  - `"ContractNode"`: 展开到节点数据
  - `"ContractQuotationRelation"`: 展开到签约单据
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
/env    # 查看当前环境
输入序号切换
```

支持环境：`nrs-escrow`（测试）、`offline-beta`（基准）

---

## 个人贡献

### （1）本体论驱动的多跳查询引擎

基于本体论思想对数据实体及关联关系建模，通过知识图谱连接本体模型。结合 LLM 语义识别能力，用户只需描述意图（如"查询订单825xxx的签约单据"），引擎自动规划多跳查询路径（Order→Contract→ContractQuotationRelation）并并行执行。

### （2）YAML 驱动的字段映射架构

通过 Gateway 聚合数据实体，YAML 统一管理接口字段与本体属性的映射关系。引擎解析映射配置，实现字段映射与业务逻辑解耦，新增实体无需改代码。

### （3）多 Agent 架构与主路由设计

设计 RouterNode 作为主 Agent，兼具意图路由和直接回答能力。下设 QueryAgent（数据查询）和 InvestigateAgent（问题排查）两个子 Agent，统一会话记忆管理，实现查询与排查两种核心能力。

---

## 技术栈

- **框架：** Spring AI + Spring Boot 3.x
- **模型：** Qwen-Turbo（通义千问）
- **并行执行：** Java CompletableFuture
- **终端：** JLine（支持 Tab 补全、历史记录）
- **测试：** JUnit 5 + Spring Boot Test

## 项目结构

```
src/main/java/.../
  trigger/agent/      # @Tool 工具类（按业务领域拆分）
    ├── OntologyQueryTool.java   # 本体论统一查询入口（推荐）
    ├── ContractQueryTool.java   # 合同查询（旧工具，逐步废弃）
    └── PersonalQuoteTool.java   # 个性化报价查询
  domain/ontology/    # 本体论领域（核心）
    ├── model/        # 本体模型
    ├── service/      # 实体注册中心
    ├── engine/       # 查询引擎
    └── gateway/      # 实体数据网关实现
  infrastructure/
    ├── annotation/   # 注解（@DataQueryTool）
    ├── client/       # HTTP 客户端
    └── service/      # 基础设施服务
  config/node/        # Agent 节点配置
    ├── RouterNode.java        # 主路由节点（意图识别 + 直接回答）
    ├── QueryAgentNode.java    # 数据查询节点
    ├── InvestigateAgentNode   # 问题排查节点（通过 AgentNode 封装）
    └── AdminNode.java          # 后台管理节点（环境切换）
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

## 架构升级历史

### v3.0 - RouterNode 作为主 Agent (2026-04-02)

#### 架构升级

将 RouterNode 升级为主 Agent，统一管理会话记忆，具备意图路由和直接回答两种能力。

#### 核心变更

1. **RouterNode 升级**
   - 新增 `answer` 意图分类（系统能力询问、输入模糊场景）
   - 低置信度非 admin 输入直接回答，不再路由到 AdminNode
   - 5 参数构造函数：新增 `SkillRegistry` + `EntityRegistry`
   - 新增 `generateDirectAnswer()` 和 `buildAvailableCapabilities()` 方法

2. **AdminNode 精简**
   - 单参数构造函数：仅保留 `EnvironmentConfig`
   - 移除 LLM 推荐、能力列表构建等逻辑
   - 职责聚焦：纯后台管理（环境切换）

3. **统一会话记忆**
   - 使用 Spring AI 标准 `MessageWindowChatMemory` 替代自定义 `SessionContextManager`
   - RouterNode 统一读取上下文
   - `SREAgentGraphProcess.processStream()` 在完成后回写记忆

4. **事件分发优化**
   - `SREAgentNodeName`: ADMIN displayTitle 改为"后台管理"
   - `SREAgentEventDispatcher`: ROUTER 节点支持 `done` 分支，直接回答时输出 Markdown 事件

5. **条件边扩展**
   - StateGraph 新增 `"done" → END` 条件边
   - RouterDispatcher 返回 `"done"` 时直接结束流程

#### 架构图

```
用户输入
    │
    ▼
┌─────────────────┐
│   RouterNode    │ ◀── 主 Agent（统一记忆管理）
│  ┌───────────┐  │
│  │意图识别   │  │
│  └───────────┘  │
│        │        │
│   ┌────┴────┐   │
│   ▼         ▼   │
│ answer    其他意图
│   │         │   │
│   ▼         ▼   │
│ 直接回答   路由  │
└─────────────────┘
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
┌──────┐ ┌──────────┐ ┌──────┐
│Query │ │Investigate│ │Admin │
│Agent │ │  Agent   │ │ Node │
└──────┘ └──────────┘ └──────┘
    │         │         │
    ▼         ▼         ▼
  END       END       END
```

### v2.5 - 基础设施层重构 (2026-03-17)

#### 重构目标

将 HTTP 调用能力从 Agent 工具层下沉到基础设施层，实现关注点分离。

#### 核心变更

1. **新建 `HttpEndpointClient`**（`infrastructure/client/`）
   - 纯 HTTP 调用基础设施，供 Gateway 和 Tool 内部调用
   - 提供 `callPredefinedEndpointRaw()` 和 `callPredefinedEndpointFiltered()` 方法
   - 无 `@Tool` 注解，不暴露给 Agent

2. **删除 `HttpEndpointTool`**（原 `trigger/agent/`）
   - HTTP 调用属于基础设施，不应作为 Agent 工具暴露
   - 移除 `AgentConfiguration` 中的工具注册

3. **更新所有 Gateway 类**
   - `BudgetBillGateway`、`ContractFormGateway`、`SubOrderGateway` 改用 `HttpEndpointClient`
   - `PersonalQuoteTool`、`ContractFormGatewayImpl` 同步更新

4. **清理相关测试**
   - 删除 `HttpEndpointToolIT.java`
   - 移除 `IntentRecognitionIT` 中 `listAvailableEndpoints` 相关测试

#### 分层架构

```
trigger/agent/          ← Agent 工具层（LLM 可调用）
    ├── OntologyQueryTool
    └── PersonalQuoteTool

infrastructure/client/  ← 基础设施层（内部调用）
    └── HttpEndpointClient

domain/ontology/gateway/ ← 数据网关层
    ├── BudgetBillGateway
    ├── ContractFormGateway
    └── SubOrderGateway
```

### v2.4 - QueryScope 枚举类 (2026-03-16)

#### 新增功能

**QueryScope 枚举类**：为 `queryScope` 参数提供类型安全的枚举支持。

```java
public enum QueryScope {
    DEFAULT("default", "仅返回起始实体"),
    CONTRACT("Contract", "展开到合同实体"),
    CONTRACT_NODE("ContractNode", "展开到合同节点"),
    // ... 更多实体
}
```

#### 其他优化

- **删除废弃代码**: 移除未使用的 `OntologyQueryPlannerTool`
- **OrderGateway 简化**: Order 作为虚拟实体，只返回 `projectOrderId`，Contract 通过关系展开获取
- **DirectOutput 跨线程支持**: 使用 `requestId + ConcurrentHashMap` 替代 `ThreadLocal`

---

### v2.3 - 多目标查询优化 (2026-03-15)

#### 新增功能

**多目标查询**：`queryScope` 支持逗号分隔多个目标实体，只查询用户需要的数据。

```java
// "825123110000002753合同的签约单据和节点" → 从订单出发，展开到多个目标
ontologyQuery(entity="Order", value="825123110000002753", queryScope="ContractNode,ContractQuotationRelation")
```

#### 性能提升

| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 用户要"签约单据和节点" | 查询所有关联数据（fields、form、config 等） | 只查询 nodes、signedObjects |
| 工具耗时 | 36秒+ | 2-3秒 |

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

---

### v2.1 - 本体论驱动并行查询引擎 (2026-03)

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

---

## 许可证

MIT License
