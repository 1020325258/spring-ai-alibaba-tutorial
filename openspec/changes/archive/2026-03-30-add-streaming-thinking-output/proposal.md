## Why

当前 SRE-Agent 在响应用户请求时，只有在 Agent 完整执行完毕后一次性返回结果。用户无法在排查过程中实时看到 agent 调用了哪些工具、查询到了哪些数据、每个阶段得出的结论是什么。这导致当用户不认可最终结论时，无法追溯 agent 的执行操作和数据来源。

## What Changes

1. **新增流式排查过程输出**：每个工具调用完成后，实时输出该步骤的排查结果（工具名称、参数、结果摘要）
2. **扩展 SSE 事件类型**：支持 JSON 格式的 `thinking`（排查过程）和 `conclusion`（最终结论）事件类型
3. **前端适配**：解析新的事件类型，渲染为可折叠的排查过程卡片

## Capabilities

### New Capabilities
- `streaming-thinking-output`: 流式排查过程输出能力，包含工具调用追踪和实时过程展示

### Modified Capabilities
- 无

## Impact

- **后端**：06-SRE-Agent 的 SREAgentGraphProcess、ThinkingEventPublisher、ObservabilityAspect、ThinkingContextHolder、ChatController
- **前端**：07-ChatUI 的 useChat.ts、App.vue
- **依赖**：TracingService（已有）、ThinkingOutputService（已有）
