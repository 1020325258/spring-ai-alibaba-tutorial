## Context

SREAgentGraph 目前通过覆写 streamMessages() 绕过 StateGraph 执行，直接调用 queryAgent 或
investigateAgent。绕过的原因是 StateGraph.stream() 输出 NodeOutput，经 extractMessages() 过滤
后为空流（extractMessages 只接受 StreamingOutput 类型，普通 NodeAction 返回的 Map 被完全丢弃）。

解决思路：不应为了输出格式而绕过 graph，应让 graph 正常执行，在图完成后将 NodeOutput 中的
state["result"] 转换为 AssistantMessage。

## Goals / Non-Goals

**Goals:**
- 会话历史存入 OverAllState，随 CheckpointSaver 按 threadId 自动持久化/恢复
- 整个 graph 流（MemoryNode → RouterNode → AgentNode）均可读取历史
- ContextInjectionStrategy 可插拔，便于调优注入内容
- 现有集成测试零改动

**Non-Goals:**
- 跨重启持久化（MemorySaver 进程内方案，重启后历史不作保证）
- 子 Agent 的子 Agent 自动接收历史（止步于直接子 Agent，更深层通过工具调用参数传递）

## Decisions

### 决策 1：MemoryNode 作为第一个图节点

MemoryNode 从 OverAllState 读取 CheckpointSaver 恢复的 conversationHistory，
调用 injectionStrategy.inject() 生成 enrichedInput 写回 state。
RouterNode 和 AgentNode 均从 state 读 enrichedInput，无需感知历史管理逻辑。

**备选**：在 streamMessages() 里手动注入后再调图 —— 历史管理在图外，不符合 OverAllState
作为单一数据流通道的设计意图，且无法受益于 CheckpointSaver 的自动恢复。

### 决策 2：ContextInjectionStrategy 接口

注入逻辑与编排逻辑解耦。内置两种实现：
- `FullHistoryStrategy`（默认）：注入全部 H/A 摘要对
- `SlidingWindowStrategy(int n)`：注入最近 N 轮，适合历史较长的场景

SREAgentGraph 以 Spring Bean 注入策略，可通过 AgentConfiguration 替换实现。

### 决策 3：移除 streamMessages() 绕过，输出转换后置

streamMessages(String, String threadId) 调用 compiledGraph.stream(inputs, config{threadId})，
收到 Flux<NodeOutput> 后过滤出终态节点（queryAgent / investigateAgent），从 state["result"]
提取文本，构造 AssistantMessage 返回。输出格式问题在 graph 外解决，不污染 graph 内部逻辑。

### 决策 4：MessagesModelHook 保护子 Agent token 上限

queryAgent 和 investigateAgent 各加 MessagesModelHook(maxMessages=20)，限制单次 ReAct 循环
传入 LLM 的消息数。不删除 checkpoint 历史，只控制每次调用的输入窗口大小。

## Risks / Trade-offs

- **[Trade-off] 失去 token 级流式输出**：AgentNode 内部 blockLast() 同步等待，最终一次性输出。
  排查类结论本就有明确终态，可接受；查询类 JSON 输出已是一次性发送，行为不变。
- **[风险] MemorySaver 单节点内存增长**：conversationHistory 追加无界。
  Mitigation：SlidingWindowStrategy 可限制注入轮数；生产替换 RedisSaver + TTL。
- **[风险] conversationHistory AppendStrategy 与 CheckpointSaver 的交互**：每次图执行后
  CheckpointSaver 保存完整 OverAllState（含 conversationHistory），下次加载时恢复，MemoryNode
  只需追加本轮结果，无需手动管理历史列表。

## Migration Plan

1. 无数据迁移，纯代码变更
2. 部署后新会话立即生效；旧 thread（重启前）历史因 MemorySaver 不跨重启而丢失（可接受）
3. 回滚：revert 相关文件，行为恢复为当前无历史模式
