## Context

SREmate 是 Spring AI Alibaba 教程系列第 5 个项目，定位为**智能 SRE 值班助手**。v1 历经多轮迭代，从最初的"LLM 串行调用多工具"演进为"本体论驱动的一次调用并行执行"架构。

**当前架构层次：**
```
trigger/agent/     ← @Tool 工具层（面向 LLM）
domain/ontology/   ← 本体论引擎（核心业务逻辑）
infrastructure/    ← 基础设施（HTTP、注解、模板）
resources/ontology/ ← 领域配置（YAML 驱动）
```

## Goals / Non-Goals

**Goals:**
- 归档 v1 的核心架构决策，防止设计背景随时间丢失
- 描述本体论引擎的实现原理，供 v2 改进参考
- 记录已知技术负债，明确 v2 的改进空间

**Non-Goals:**
- 不修改任何代码
- 不讨论 v2 的具体实现方案

## Decisions

### 决策 1：LLM 只做意图识别，不参与执行

**问题**：传统 Agent 让 LLM 决定每一步调用哪个工具，导致串行执行，耗时 30+ 秒。

**选择**：LLM 调用一次 `ontologyQuery(entity, value, queryScope)`，引擎接管后续所有查询。

**理由**：
- LLM 擅长意图识别，不擅长执行编排
- 引擎可以基于 YAML 关系图静态分析依赖，并行执行无依赖的查询
- 新增实体不需要修改 LLM 提示词的工具清单

**对比选项**：让 LLM 调用多个细粒度工具 → 串行，每次都需要 LLM 决策，受限于 LLM 上下文窗口

---

### 决策 2：本体配置用 YAML 驱动，不硬编码

**问题**：新增实体时，如果硬编码在 Java 里，每次都要修改引擎代码。

**选择**：`domain-ontology.yaml` 定义实体属性、查询策略、实体间关系；引擎读取 YAML 动态路径规划。

**理由**：
- 新增实体只需"实现 Gateway + 添加 YAML 配置"，不改引擎
- YAML 可视化编辑，业务人员可参与维护
- 引擎的路径查找（BFS）是通用的，与具体业务无关

**当前实体关系图：**
```
Order ──────────────────→ Contract
  │                          │
  ├─→ BudgetBill             ├─→ ContractNode
  │       │                  ├─→ ContractQuotationRelation
  │       └─→ SubOrder       ├─→ ContractField
  │                          ├─→ ContractForm
  └─→ PersonalQuote          └─→ ContractConfig
```

---

### 决策 3：@DataQueryTool 注解驱动直接输出

**问题**：工具返回数据后，LLM 重新"翻译"一遍，额外消耗 5-10 秒且引入歧义。

**选择**：最外层工具标记 `@DataQueryTool`，AOP 切面捕获结果后直接流式输出，跳过 LLM 归纳。

**关键约束**：只能标记在用户直调的工具上，内部工具禁止标记（否则中间结果被捕获，丢失最终结果）。

**性能效果**：
| 场景 | 优化前 | 优化后 |
|------|--------|--------|
| 单合同查询 | 8 秒 | 1-2 秒 |
| 订单→多合同→关联数据 | 16-20 秒 | 2-4 秒 |

---

### 决策 4：queryScope 参数控制展开深度

**问题**：默认展开所有关联数据会查询大量不需要的数据（如用户只要"节点"，却查了 fields、form、config）。

**选择**：`queryScope` 参数精确控制目标实体，支持逗号分隔多目标，引擎只沿需要的路径展开。

**演进历史**：
- v1 早期：有 `SCOPE_ALIAS`（`form`→`ContractForm` 等简写） → 已删除，统一用实体名
- v1 当前：`QueryScope` 枚举 + 字符串回退（支持未注册的实体名）

---

### 决策 5：集成测试验证工具调用行为，不验证输出内容

**问题**：验证 LLM 输出内容非常脆弱（业务数据变化、LLM 回答措辞变化都会导致测试失败）。

**选择**：集成测试只断言"哪个工具被调用了"，不关心返回了什么内容。

**关键教训**：修改工具参数的合法取值范围时，必须同步更新 `sre-agent.md` 提示词，否则 LLM 会用旧格式调用工具，集成测试才能暴露这类问题。

## Risks / Trade-offs

- **[风险] YAML 本体配置与代码不同步** → 规范：修改 YAML 必须同步修改 Gateway；集成测试可验证
- **[风险] 提示词与代码参数不同步** → 规范：修改工具参数合法值必须同步更新 sre-agent.md，并运行集成测试
- **[负债] ContractQueryTool 仍存在** → 旧工具未删除，可能导致 LLM 混用；v2 应彻底移除
- **[负债] PersonalQuote 需额外参数** → 通过 `extraParams` 特殊处理，破坏了"一次调用"的简洁性；v2 可考虑将其纳入本体配置
- **[负债] ExecutorService 硬编码线程数** → `Executors.newFixedThreadPool(10)` 未配置化，高并发场景需调整
- **[已知问题] SubOrder 双参数查询** → `queryByFieldWithContext` 传递父记录上下文，是 Gateway 接口的特例，破坏了 Gateway 的统一性
