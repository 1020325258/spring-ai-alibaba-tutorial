## Context

当前 SRE-Agent 的响应流程：用户请求 → AgentNode 内部调用 `agent.streamMessages()` → `blockLast()` 等待完整结果 → 写入 `state["result"]` → 通过 SSE 一次性返回完整字符串。

现有基础设施：
- `TracingService`：已记录工具调用链路（startToolCall/endToolCall）
- `ThinkingOutputService`：已实现构建 Thinking 块 Markdown（但未使用）
- `SREAgentGraphProcess`：已有 SSE 流式返回机制
- `ObservabilityAspect`：AOP 拦截工具调用，自动记录 TracingContext

## Goals / Non-Goals

**Goals:**
- 实现实时流式排查过程输出（每个工具调用完成后立即输出该步骤结果）
- 扩展 SSE 事件类型，支持 JSON 格式的 `thinking` 和 `conclusion`
- 前端能够区分渲染排查过程和最终结论

**Non-Goals:**
- 不修改 StateGraph 的可视化能力
- 不修改现有的日志和指标收集机制
- 不修改现有的测试用例

## Decisions

### 决策 1: SSE 事件格式选择 JSON 而非 Markdown

**选择**：JSON 结构化格式 `{"type": "thinking", "content": "..."}`

**理由**：
- 前端可以精确解析和渲染不同类型的内容
- `thinking` 类型渲染为可折叠卡片，`conclusion` 类型渲染为普通文本
- 更容易实现复杂的交互（如点击展开/收起）

### 决策 2: 排查过程实时推送而非批量输出

**选择**：实时推送（每个工具调用完成后立即输出）

**理由**：
- 用户可以实时看到排查进展，更有交互感
- 与"流式响应"的整体目标一致

### 决策 3: 复用现有 TracingService 而非新增追踪机制

**选择**：在 `ObservabilityAspect` 中集成 ThinkingEventPublisher

**理由**：
- TracingService 已经记录了工具调用链路
- 只需在 `endToolCall` 后触发 Thinking 事件发布
- 最小化代码侵入

## Implementation Notes

### ThinkingContextHolder 线程模型
原设计使用 `ThreadLocal` 传递 SSE sink，但 Spring AI 工具调用发生在 Reactor 的 `boundedElastic` 线程上，与 executor 线程不同，导致 `ThreadLocal.get()` 返回 null。

**实际实现**：改用 `AtomicReference<ThinkingContext>` 静态字段，任意线程均可读取。适用于单会话场景（教程演示用途）。

### 路由器 thinking 事件
Router 节点直接在 `buildStreamingContent()` 中生成 thinking JSON（内容为非结构化字符串），前端 `parseThinkingContent()` 增加了 fallback 逻辑：当无法解析 `**步骤N - 标题**` 格式时，将整个内容去掉 Markdown 符号后作为标题展示。

### 前端渲染扩展
App.vue 新增：
- `thinkingBlocks`：collapsible `<details>` 卡片，展示工具名、参数、结果摘要、耗时、成功状态
- `conclusion`：使用 MarkdownRender `:content` prop 渲染；无 conclusion 时 fallback 到 `:nodes` prop（兼容纯文本流）

## Risks / Trade-offs

**[风险] 并发写入 SSE 的线程安全**
- `Sinks.Many` 本身线程安全，事件顺序由工具调用的串行执行保证
- `AtomicReference` 不支持多并发会话（仅适用于演示场景）

**[风险] 排查过程输出时机**
- LLM 可能在工具调用之间输出推理内容，与排查过程交织
- mitigation：排查过程使用 `thinking` 类型，前端单独渲染在主内容之前
