## ADDED Requirements

### Requirement: 会话历史作为 OverAllState 的一部分在 graph 内流通

系统 SHALL 在 OverAllState 中维护 `conversationHistory`（H/A 摘要对列表），
graph 中每个节点均可通过 state 读取该字段。

#### Scenario: MemoryNode 读取历史并生成 enrichedInput
- **WHEN** graph 开始执行且 conversationHistory 非空
- **THEN** MemoryNode 调用 injectionStrategy.inject(history, input) 生成 enrichedInput
- **THEN** RouterNode 和 AgentNode 读取 enrichedInput 而非原始 input

#### Scenario: 首轮对话无历史时正常处理
- **WHEN** conversationHistory 为空（首轮对话）
- **THEN** MemoryNode 直接将原始 input 作为 enrichedInput 写入 state
- **THEN** 后续节点行为与当前版本一致

### Requirement: 会话历史按 threadId 隔离持久化

系统 SHALL 以 MemorySaver 作为 compiledGraph 的 CheckpointSaver，
按 threadId 自动恢复和保存 OverAllState。

#### Scenario: 同一 threadId 多轮对话保留历史
- **WHEN** 用户以相同 threadId 发送第二条消息
- **THEN** CheckpointSaver 恢复上次 OverAllState，conversationHistory 包含第一轮 H/A 对
- **THEN** AgentNode 收到的 enrichedInput 中包含第一轮历史上下文

#### Scenario: 不同 threadId 历史完全隔离
- **WHEN** 用户 A 和用户 B 使用不同 threadId 分别发送消息
- **THEN** 两者的 conversationHistory 互不可见，各自独立

### Requirement: ContextInjectionStrategy 可插拔替换

系统 SHALL 通过 ContextInjectionStrategy 接口控制注入内容，
默认使用 FullHistoryStrategy，可通过替换 Spring Bean 切换为 SlidingWindowStrategy 或自定义实现。

#### Scenario: SlidingWindowStrategy 限制注入轮数
- **WHEN** SlidingWindowStrategy(n=3) 被配置为当前策略
- **THEN** enrichedInput 中仅包含最近 3 轮 H/A 对，不包含更早的历史

### Requirement: threadId 缺失时降级为无状态模式

当 RunSseFilter 解析不到 threadId 时，系统 SHALL 调用无参 streamMessages()，
行为与当前版本一致，不得抛出异常或返回错误。

#### Scenario: 无 threadId 的请求正常响应
- **WHEN** POST `/run_sse` 请求体中没有 `threadId` 字段
- **THEN** 系统调用 streamMessages(input)，无历史上下文，正常返回响应

### Requirement: 现有集成测试零改动

系统 SHALL 保持无参 streamMessages(String) 重载可用，
所有现有集成测试（BaseSREAgentIT.ask()）无需修改即可继续通过。
