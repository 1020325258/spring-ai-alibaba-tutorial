## Why

RouterNode 当前使用硬编码的粗粒度分类 Prompt（query/investigate/admin/unclear），无法识别症状语义相似但表述不同的排查场景（如"缺少定软电品类报价"被误路由到 admin）。同时路由逻辑与 Skill 选择分散在两处（RouterNode Prompt + investigate-agent.md 触发表），新增 Skill 需同步修改多个文件。

## What Changes

- **新增** `SkillRoutingStrategy` 接口，抽象意图路由策略，支持多实现替换
- **新增** `LlmSkillRoutingStrategy`：从 SkillRegistry 动态读取所有 Skill 的 `name` + `description`，注入 RouterNode Prompt，LLM 直接在 Skill 粒度上做路由决策
- **修改** `RouterNode`：路由逻辑委托给 `SkillRoutingStrategy`，当返回值为 Skill name 时路由到 `investigateAgent` 并写入 `state["selectedSkill"]`
- **修改** State：新增 `selectedSkill` 字段，传递 RouterNode 选定的 Skill name
- **修改** `investigateAgent` Prompt（investigate-agent.md）：移除硬编码触发规则表，改为直接使用 `state["selectedSkill"]` 调用 `readSkill`
- **新增** `sre.routing.strategy` 配置项，支持未来切换路由策略实现

## Capabilities

### New Capabilities

- `skill-routing-strategy`: 可插拔的意图路由策略接口及其 LLM Prompt 实现，从 SkillRegistry 动态加载 Skill 描述，在 Skill 粒度上完成意图识别

### Modified Capabilities

## Impact

- `config/node/RouterNode.java`：路由逻辑重构，依赖注入 `SkillRoutingStrategy`
- `config/SREAgentGraphConfiguration.java`：State 新增 `selectedSkill` 字段
- `config/AgentConfiguration.java`：investigateAgent 构建时注入 `selectedSkill` 上下文
- `resources/prompts/investigate-agent.md`：删除 Skill 触发规则表章节
- 新增类：`SkillRoutingStrategy`（接口）、`LlmSkillRoutingStrategy`（实现）
- 无 API 变更，无外部依赖变更
