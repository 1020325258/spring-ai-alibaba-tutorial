## Why

当前 SREAgentGraph.streamMessages() 绕过 compiledGraph 直接调用子 Agent，导致：
1. 会话历史无处挂载，每次调用均从空状态启动，多轮对话上下文丢失
2. 路由 LLM 无法理解追问含义（如"这个合同"），用户必须重复说明实体
3. 旧的子 Agent saver 方案造成历史孤岛（queryAgent 与 investigateAgent 互不可见）

## What Changes

- 新增 `MemoryNode` 作为图的第一个节点，负责从 OverAllState 读取会话历史并注入 enrichedInput
- 新增 `ContextInjectionStrategy` 接口（含 FullHistoryStrategy / SlidingWindowStrategy 两种内置实现），控制注入内容，预留扩展点
- 移除 `streamMessages()` 对 compiledGraph 的绕过，改为真正执行图并在图完成后做输出格式转换
- SREAgentGraph 编译时注入 MemorySaver，CheckpointSaver 自动按 threadId 持久化 OverAllState
- queryAgent / investigateAgent 保持无状态（不注入 saver），接收 enrichedInput 作为任务上下文
- RunSseFilter 解析请求体中的 threadId，传入新增的 streamMessages(String, String) 重载
- queryAgent / investigateAgent 各增加 MessagesModelHook(maxMessages=20) 防止单次 ReAct 循环 token 无限增长

## Capabilities

### New Capabilities

- `graph-native-session-memory`：会话历史作为 OverAllState 的一部分在整个 graph 流中流通，
  queryAgent 和 investigateAgent 均可感知多轮上下文，支持追问（如"这个合同的节点呢？"）

### Modified Capabilities

- `sre-agent-routing`：路由器现在基于 enrichedInput（含历史）判断意图，追问路由更准确

## Impact

- **代码**：SREAgentGraph.java、AgentConfiguration.java、RunSseFilter.java，新增 ContextInjectionStrategy 接口及实现
- **依赖**：无新依赖，MemorySaver 已在 classpath
- **测试**：现有集成测试走无参 streamMessages() 降级路径，零改动
