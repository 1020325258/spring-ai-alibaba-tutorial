## ADDED Requirements

### Requirement: 统一事件分发接口
系统 SHALL 提供 SREAgentEventDispatcher 统一接收所有 NodeOutput 并构建 SSE 事件。

#### Scenario: 分发器接收 NodeOutput
- **WHEN** SREAgentEventDispatcher.dispatch(output, sink) 被调用
- **THEN** 根据 output.node() 获取 nodeName，匹配对应的 SREAgentNodeName 枚举，构建差异化事件

#### Scenario: 不同节点构建不同事件结构
- **WHEN** 分发器处理不同 nodeName 的 NodeOutput
- **THEN** router 节点构建 ThinkingEvent（带 routingTarget）；queryAgent 构建 conclusion（带 JSON 内容）；investigateAgent/admin 构建 Markdown 文本；tool_call 构建工具调用事件

### Requirement: 事件结构统一使用 nodeName
SSE 事件 SHALL 使用 nodeName 字段替代 type 字段区分事件类型。

#### Scenario: 事件包含 nodeName 字段
- **WHEN** 任何节点输出事件
- **THEN** 事件 JSON 包含 "nodeName" 字段（如 "router"、"queryAgent"）

#### Scenario: 事件包含 displayTitle 字段
- **WHEN** 任何节点输出事件
- **THEN** 事件 JSON 包含 "displayTitle" 字段（如 "意图识别"、"数据查询"）

### Requirement: 移除 stepNumber
事件 SHALL 移除 stepNumber 字段，使用 displayTitle 提供更有意义的展示信息。

#### Scenario: 前端展示 displayTitle
- **WHEN** 前端解析 SSE 事件
- **THEN** 使用 displayTitle 字段展示步骤标题，而非数字序号

### Requirement: ObservabilityAspect 实时发送工具事件
工具调用切面 SHALL 在每个 @Tool 方法执行完成后立即发送事件到 SSE，而非等待节点完成。

#### Scenario: 工具调用完成后立即发送
- **WHEN** @Tool 方法执行完成（成功或失败）
- **THEN** ObservabilityAspect 立即将 ThinkingEvent 发送到 sink，前端实时展示

#### Scenario: 工具调用事件包含完整信息
- **WHEN** 工具调用事件发送
- **THEN** 事件包含 toolName、params、resultSummary、duration、success 等字段