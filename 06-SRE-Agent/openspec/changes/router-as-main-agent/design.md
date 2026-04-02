## Context

当前 SRE-Agent 的 StateGraph 拓扑：`START → router → [queryAgent | investigateAgent | admin] → END`。RouterNode 使用同步 `chatModel.call()` 输出一个 JSON 意图分类，AdminNode 负责环境管理和兜底引导（借助 SkillRegistry + EntityRegistry 生成能力推荐）。记忆（`MessageWindowChatMemory`）已在上一次重构中注入到 RouterNode，但 AdminNode 的引导逻辑仍是对话入口的一部分，导致职责边界模糊。

DeepResearch 的 `CoordinatorNode` 是参考模型：它既能判断是否需要深度研究（路由），也能在简单问题上直接输出回答，两条路径共用同一上下文入口。

## Goals / Non-Goals

**Goals:**
- RouterNode 作为唯一上下文入口，支持 `answer` 意图直接回答
- AdminNode 职责收拢为纯后台管理（无 LLM 调用）
- 记忆写回路径不变（`processStream.doOnComplete` 已覆盖所有路径）
- 条件边 `"done" → END` 支持 RouterNode 直接结束流程

**Non-Goals:**
- 不给 RouterNode 加流式输出（当前架构 SSE 流由 NodeOutput 驱动，同步调用足够）
- 不修改 QueryAgentNode 和 AgentNode（investigateAgent）
- 不改变 AdminNode 的环境切换功能

## Decisions

### Decision 1：RouterNode 使用两次同步 LLM 调用，而非单次工具调用

**选项 A（选定）：两次同步调用**
- 第一次：意图分类（返回 JSON：intent + confidence + extractedParams）
- 第二次：仅在 intent=="answer" 或低置信度时，生成直接回答文本

**选项 B：单次工具调用模式**
RouterNode 绑定 `routeToQueryAgent()`/`routeToInvestigateAgent()` 等工具，LLM 无工具调用时视为直接回答。

选定 A 的理由：当前 RouterNode 已有稳定的 JSON 解析逻辑，两次调用仅增加一次 LLM 请求（仅在 answer 路径触发），对 query/investigate 路径零额外开销；工具调用模式需要重构意图识别机制，风险更高。

### Decision 2：`state["result"]` 写入由 RouterNode 负责（answer 路径）

RouterNode 在 `apply()` 中同步生成回答并写入 `state["result"]`，与 QueryAgentNode/AgentNode 的写入方式一致。`SREAgentGraphProcess.writeBackToMemory()` 的写回逻辑已通过 `state.value("result", "")` 读取，无需修改。

### Decision 3：routingTarget = "done" 映射到 StateGraph.END

通过在条件边 Map 中添加 `"done" → END`，RouterDispatcher 返回 "done" 时图直接终止。命名选 "done" 而非 "__end__"（StateGraph.END 的原始值），是为了保持可读性，Map 的值才是实际节点引用。

### Decision 4：SREAgentEventDispatcher 对 router 节点的直接回答输出 Markdown 事件

当 `routingTarget == "done"` 时，router 节点已有 `state["result"]`，复用现有 `buildMarkdownEvent(output)` 输出内容事件（displayTitle="意图识别"）。不新增节点类型，减少前端适配成本。

## Risks / Trade-offs

- **两次 LLM 调用增加延迟**：answer 路径新增一次 `generateDirectAnswer()` 调用（约 1-2s）。缓解：query/investigate/admin 路径不受影响；answer 路径本身已是"兜底"场景，延迟可接受。

- **能力推荐准确度依赖 LLM**：RouterNode 直接回答使用 `ANSWER_PROMPT` 调用 LLM，质量依赖 prompt 设计。缓解：prompt 中明确提供 SkillRegistry + EntityRegistry 列表，减少幻觉。

- **原 admin 引导场景可能存在测试覆盖缺口**：如果集成测试中有依赖 AdminNode 做引导的 qa-pairs，路由路径变化后需更新期望。缓解：运行 `QaPairEvaluationIT` 验证语义等价性。

## Migration Plan

1. 一次性提交所有变更（RouterNode + AdminNode + EventDispatcher + Configuration）
2. 编译通过后运行单元测试
3. 运行集成测试（`./run-integration-tests.sh`）；若 qa-pairs 中有 admin 场景失败，检查语义是否等价并更新期望
4. 无数据库变更，无需回滚计划

## Open Questions

- RouterNode 直接回答时，`displayTitle="意图识别"` 显示在前端是否合适？可考虑改为"智能引导"，但需前端同步适配——暂不处理，后续 polish。
