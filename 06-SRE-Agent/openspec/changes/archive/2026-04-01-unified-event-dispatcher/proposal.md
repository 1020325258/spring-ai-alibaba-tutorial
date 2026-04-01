## Why

当前 SRE-Agent 的 SSE 事件发送逻辑分散在 3 处（RouterNode、ObservabilityAspect、QueryAgent 节点），且使用 `stepNumber` 数字序号展示步骤不够直观。前端解析基于 `type` 字段，与后端设计不统一，参考 DeepResearch 项目的 nodeName 驱动设计进行重构。

## What Changes

1. **新增节点枚举 `SREAgentNodeName`**
   - 统一管理 SRE-Agent 所有节点名称（router、queryAgent、investigateAgent、admin、tool_call）
   - 映射对应的 `displayTitle`（如"意图识别"、"数据查询"、"问题排查"）

2. **新增统一事件分发器 `SREAgentEventDispatcher`**
   - 替代 `SREAgentGraphProcess.resolveContent()` 的分散逻辑
   - 基于 `nodeName` 匹配，构建差异化事件结构

3. **改造 `ObservabilityAspect`**
   - 移除直接发送事件的逻辑
   - 改为将工具调用事件收集到 `ThinkingContextHolder`

4. **移除 `stepNumber`**
   - 事件结构中移除 `stepNumber` 字段
   - 使用 `displayTitle`（节点中文标题）替代序号展示

5. **前端解析改造**
   - 解析逻辑从基于 `type` 改为基于 `nodeName`
   - 参考 DeepResearch 的 `findNode(nodeName)` 模式

## Capabilities

### New Capabilities
- `unified-event-dispatcher`: 统一事件分发机制，基于 nodeName 差异化构建和解析 SSE 事件
- `node-name-enum`: 节点名称枚举，定义所有 Agent 节点的名称和展示标题映射

### Modified Capabilities
- 无（现有功能的行为不变，只是实现方式重构）

## Impact

- **后端**：`06-SRE-Agent/src/main/java/com/yycome/sreagent/config/` 新增枚举和分发器，修改现有事件发送逻辑
- **前端**：`07-ChatUI/frontend/src/composables/useChat.ts` 解析逻辑改造
- **数据结构**：SSE 事件从 `type` 字段区分改为 `nodeName` 字段区分，`stepNumber` 改为 `displayTitle`