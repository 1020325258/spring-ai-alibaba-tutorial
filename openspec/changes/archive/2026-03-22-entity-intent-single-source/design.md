## Context

当前 SREmate 的实体意图识别存在三处重复维护：
1. `domain-ontology.yaml`: 实体定义 + aliases
2. `sre-agent.md`: 决策表（硬编码 entity 与编号格式的对应关系）
3. `OntologyQueryTool.inferEntityFromValue()`: 代码层的格式推断逻辑

这导致：
- 用户输入 `instanceId` 查询版式时，纯数字格式被错误推断为 `Order`
- 新增实体需同步修改三处，维护成本高

**目标状态**：YAML 作为单一数据源，LLM 基于注入的实体摘要做意图识别，代码仅做合法性校验。

## Goals / Non-Goals

**Goals:**
- 实现"单一数据源"：实体别名仅在 YAML 维护，自动注入提示词
- 恢复 LLM 决策灵活性：移除代码层的格式推断，信任 LLM 判断
- 支持用户直接输入 `instanceId` 查询 `ContractInstance`

**Non-Goals:**
- 不改变 `ontologyQuery` 工具的 API 签名
- 不改变查询引擎的核心逻辑
- 不处理 `queryScope` 的推断（仍由 LLM 决定）

## Decisions

### Decision 1: 实体重命名 ContractForm → ContractInstance

**选择**: 重命名实体
**理由**: "版式"是业务术语，"实例"更贴近用户直觉。用户说"查实例信息"比"查版式"更自然
**别名扩展**: `["实例", "实例信息", "版式", "版式数据", "form_id", "版式ID", "instanceId"]`
**替代方案**: 保留 ContractForm 名称，仅扩展别名 → 放弃，因为"版式"术语对用户不够直观

### Decision 2: EntityRegistry 新增 getEntitySummaryForPrompt()

**选择**: 在 EntityRegistry 中新增方法
**输出格式**:
```
【可用实体】
Order(订单): 别名[订单, 项目订单]
  查询入口: projectOrderId（纯数字订单号）
Contract(合同): 别名[合同, 合同数据, 合同信息]
  查询入口: contractCode（C开头合同号）, projectOrderId（订单号）
ContractInstance(合同实例): 别名[实例, 实例信息, 版式, instanceId]
  查询入口: instanceId（实例ID）
```
**注入位置**: `sre-agent.md` 中使用 `{{entity_summary}}` 占位符，AgentConfiguration 中替换

### Decision 3: inferEntityFromValue 改为纯校验

**选择**: 移除格式推断逻辑，仅保留 entity 合法性校验
**理由**: 格式推断覆盖了 LLM 的判断，导致纯数字 instanceId 被误识别为 Order
**替代方案**: 保留推断但增加 queryScope 参数辅助判断 → 放弃，增加复杂度且仍可能冲突

### Decision 4: 提示词精简

**选择**: 移除硬编码决策表，保留 3-5 个典型样例
**理由**: 决策表与 YAML 容易脱节，样例足以让 LLM 理解模式
**保留内容**:
- 实体摘要（自动注入）
- 决策规则说明（意图优先 + 上下文推断）
- 少量样例表格

## Risks / Trade-offs

**风险 1: LLM 判断不稳定**
- 描述: 不同模型或版本对相同输入可能给出不同 entity
- 缓解: 提供清晰的样例，测试覆盖典型场景

**风险 2: 提示词过长**
- 描述: 实体摘要可能增加 token 消耗
- 缓解: 精简摘要格式，仅包含必要信息

**风险 3: 现有测试需要更新**
- 描述: `inferEntityFromValue` 的测试需要修改
- 缓解: 测试改为验证校验逻辑而非推断逻辑
