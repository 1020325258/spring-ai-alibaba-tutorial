## Why

当用户输入模糊或无法匹配到具体 Skill/实体时，路由器将请求转发给 admin 节点。当前 admin 节点只能返回"我不理解，请提供更多信息"，缺乏引导能力，用户体验差。

用户输入"826033014000004927 可签约S单"本意是查询 `PersonalSignableOrderInfo`，但路由器未能识别"可签约S单"这一表述，导致路由到 admin。如果 admin 能智能推荐"您可能想查询：销售合同弹窗可签约S单"，用户即可澄清意图。

## What Changes

- **AdminNode 增强**：不再直接返回"无法理解"，而是调用 LLM 分析用户输入 + 可用能力列表，输出 Top-K 相似能力推荐
- **能力列表构建**：动态读取 `SkillRegistry.listAll()` 和 `EntityRegistry.getAllEntities()`，生成能力描述列表供 LLM 匹配
- **推荐输出格式**：标准化"您可能想问..."输出格式，包含 1-3 个候选能力

## Capabilities

### New Capabilities

- `intent-clarification`: 当用户意图不明确时，系统通过 LLM 分析用户输入和可用能力列表，输出语义相似的能力推荐，引导用户澄清意图

### Modified Capabilities

（无现有能力需要修改）

## Impact

- **代码**：`AdminNode.java` 需要重构，增加 LLM 调用和能力列表构建逻辑
- **依赖**：复用现有 `ChatModel`，无需新增基础设施
- **延迟**：admin 路由增加一次 LLM 调用（预计 +100~300ms）
- **提示词**：新增 admin 推荐专用 prompt 模板
