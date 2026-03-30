## ADDED Requirements

### Requirement: 流式排查过程输出
当 Agent 执行工具调用时，系统 SHALL 在工具调用完成后实时输出该步骤的排查结果，包括工具名称、参数和结果摘要。

#### Scenario: 工具调用完成后输出排查过程
- **WHEN** ObservabilityAspect 拦截到工具调用完成（endToolCall）
- **THEN** 系统构建该工具调用的排查结果，包含工具名、参数、结果摘要，并通过 SSE 实时发送到前端

### Requirement: SSE 事件类型扩展
SSE 响应 SHALL 支持 JSON 格式的多种事件类型，用于区分排查过程和最终结论。

#### Scenario: thinking 类型事件
- **WHEN** Agent 执行工具调用并完成
- **THEN** SSE 输出 `{"type": "thinking", "content": "**步骤N - 工具名**\n> 工具：xxx\n> 参数：...结果摘要"}`

#### Scenario: conclusion 类型事件
- **WHEN** Agent 完成推理，准备输出最终结论
- **THEN** SSE 输出 `{"type": "conclusion", "content": "最终结论文本"}`

### Requirement: 前端解析渲染
前端 SHALL 能够解析 SSE 中的 JSON 事件类型，并分别渲染排查过程和最终结论。

#### Scenario: 渲染 thinking 类型
- **WHEN** 前端收到 type 为 "thinking" 的事件
- **THEN** 将 content 渲染为可折叠的排查过程卡片，带工具图标

#### Scenario: 渲染 conclusion 类型
- **WHEN** 前端收到 type 为 "conclusion" 的事件
- **THEN** 将 content 渲染为最终结论文本（普通消息样式）
