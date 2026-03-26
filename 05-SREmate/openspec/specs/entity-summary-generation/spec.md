## ADDED Requirements

### Requirement: 实体摘要自动生成
EntityRegistry SHALL 提供 `getEntitySummaryForPrompt()` 方法，从 `domain-ontology.yaml` 自动生成实体摘要，供 LLM 提示词使用。

#### Scenario: 生成完整实体摘要
- **WHEN** 调用 `getEntitySummaryForPrompt()`
- **THEN** 返回格式化的实体列表，包含每个实体的：
  - 名称 + displayName
  - 别名列表
  - 查询入口（lookupStrategies.field + 描述）

#### Scenario: 摘要注入提示词
- **WHEN** AgentConfiguration 构建 system prompt
- **THEN** 将 `{{entity_summary}}` 占位符替换为 `getEntitySummaryForPrompt()` 返回的内容

---

### Requirement: 单一数据源原则
实体定义（名称、别名、查询入口）SHALL 仅在 `domain-ontology.yaml` 中维护，提示词和代码 SHALL 通过 EntityRegistry 获取信息。

#### Scenario: 新增实体无需修改提示词
- **WHEN** 在 YAML 中新增实体定义（含 aliases）
- **THEN** 实体摘要自动包含新实体，无需手动修改 `sre-agent.md`

#### Scenario: 修改别名自动生效
- **WHEN** 修改 YAML 中某实体的 aliases
- **THEN** 下次启动后提示词中的实体摘要自动更新
