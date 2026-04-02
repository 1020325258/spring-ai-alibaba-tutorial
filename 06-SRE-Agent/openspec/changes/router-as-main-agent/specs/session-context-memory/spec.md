## MODIFIED Requirements

### Requirement: 历史对话上下文仅注入 RouterNode
历史对话上下文 SHALL 仅注入到 RouterNode，由 RouterNode 统一读取 `MessageWindowChatMemory.get(sessionId)` 并格式化为 prompt 文本；AgentNode（investigateAgent）不注入历史上下文。

#### Scenario: RouterNode 读取历史上下文
- **WHEN** RouterNode 收到用户请求，`sessionId` 非空
- **THEN** 从 `MessageWindowChatMemory` 读取该 session 的历史消息列表
- **AND** 将历史格式化为"最近对话记录"文本，注入到路由 prompt 的 `## 历史上下文` 段

#### Scenario: AgentNode 不注入历史上下文
- **WHEN** AgentNode（investigateAgent）执行
- **THEN** 不从 `MessageWindowChatMemory` 读取历史
- **AND** 只注入预提取参数和时效性引导（不含对话历史）

### Requirement: 对话记忆写回覆盖所有路由路径
`processStream.doOnComplete` SHALL 在图执行完成后，将本轮 `UserMessage(input)` + `AssistantMessage(result)` 写回 `MessageWindowChatMemory`，覆盖 `queryAgent`、`investigateAgent`、`admin`、`done`（RouterNode 直接回答）四条路径。

#### Scenario: RouterNode 直接回答后写回记忆
- **WHEN** RouterNode 走 `done` 路径，在 `state["result"]` 写入直接回答
- **THEN** `processStream.doOnComplete` 将 `UserMessage(input)` + `AssistantMessage(result)` 写回 `MessageWindowChatMemory`

#### Scenario: 专业 Agent 回答后写回记忆
- **WHEN** queryAgent 或 investigateAgent 执行完成，在 `state["result"]` 写入结果
- **THEN** `processStream.doOnComplete` 同样将本轮对话写回 `MessageWindowChatMemory`
