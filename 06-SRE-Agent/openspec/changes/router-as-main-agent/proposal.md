## Why

RouterNode 目前只做意图分类后路由，当意图不明确时会路由到 AdminNode 做"智能推荐"，导致记忆上下文需要分散注入两个节点，且 AdminNode 承担了与其后台管理职责不符的引导功能。将 RouterNode 升级为主 Agent，使其能够直接回答用户问题，不仅统一了记忆的注入点，也让架构职责更清晰。

## What Changes

- **RouterNode**：新增 `answer` 意图分类；当意图为 `answer` 或置信度不足时，RouterNode 调用 LLM 直接生成回答，写入 `state["result"]`，路由目标设为 `done`（→ END）；RouterNode 接管能力推荐逻辑（从 AdminNode 迁入 SkillRegistry + EntityRegistry 依赖）
- **AdminNode**：移除引导/推荐逻辑（`recommendCapabilities`、`buildAvailableCapabilities`、`buildDefaultHelpResponse`）及相关 LLM 调用，仅保留环境查看/切换的纯代码处理；移除 `ReactAgent`、`SkillRegistry`、`EntityRegistry`、`ChatModel`、`TracingService` 依赖
- **StateGraph**：条件边新增 `"done" → END` 分支，覆盖 RouterNode 直接回答的路径
- **SREAgentEventDispatcher**：ROUTER 节点事件分发新增判断——当 `routingTarget == "done"` 时输出内容事件（而非路由指示器）
- **SREAgentNodeName**：ADMIN 的 `displayTitle` 从"智能推荐"更新为"后台管理"

## Capabilities

### New Capabilities

- `router-direct-answer`：RouterNode 作为主 Agent，对 `answer` 意图或低置信度输入直接生成回答（含能力介绍、引导文本），无需路由到其他节点

### Modified Capabilities

- `session-context-memory`：记忆上下文注入点收拢到 RouterNode 一处；写回逻辑（`done` 路径）由 RouterNode 写入 `result`，由 `processStream.doOnComplete` 统一写回，无需额外修改

## Impact

- `RouterNode.java`：新增 5 参数构造函数（+SkillRegistry, +EntityRegistry）、新增 `answer` 意图、新增 `generateDirectAnswer()` 和 `buildAvailableCapabilities()` 方法
- `AdminNode.java`：构造函数缩减为 1 参数（仅 EnvironmentConfig），删除 LLM 调用路径
- `SREAgentGraphConfiguration.java`：RouterNode/AdminNode 构造参数更新，条件边 Map 新增 `"done" → END`，移除 `adminAgent` Bean 注入
- `SREAgentEventDispatcher.java`：ROUTER case 新增 `routingTarget == "done"` 分支
- `SREAgentNodeName.java`：ADMIN displayTitle 更新
- 集成测试：query/investigate 路径不受影响；原路由到 admin 的引导场景现由 RouterNode 直接处理，结果语义等价
