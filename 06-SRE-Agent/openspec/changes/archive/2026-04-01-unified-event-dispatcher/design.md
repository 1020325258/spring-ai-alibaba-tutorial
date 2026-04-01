## Context

### 当前状态
SRE-Agent 的 SSE 事件发送逻辑分散在 3 处：
1. **Router 节点**（SREAgentGraphProcess.resolveContent）：发送路由决策的 thinking 事件
2. **ObservabilityAspect**：在 @Tool 方法调用后直接发送 thinking 事件
3. **QueryAgent 节点**（SREAgentGraphProcess.resolveContent）：发送 conclusion 事件

前端解析基于 `type` 字段（`type: "thinking"` / `type: "conclusion"`），且使用 `stepNumber` 数字序号展示步骤。

### 参考对象
DeepResearch 项目的设计：
- 后端：GraphProcess.java 统一处理所有 nodeName，按 nodeName 构建差异化事件
- 前端：useMessageParser.ts 使用 findNode(nodeName) 差异化解析
- 使用 displayTitle（节点中文标题）替代序号

### 约束
- 保持现有功能行为不变，只是实现方式重构
- SSE 协议兼容（仍是 ServerSentEvent<String>）
- 不引入新的外部依赖

## Goals / Non-Goals

**Goals:**
- 统一事件分发：所有 SSE 事件从 SREAgentEventDispatcher 一个入口发出
- 基于 nodeName 差异化：后端按 nodeName 构建事件，前端按 nodeName 解析
- 移除 stepNumber：使用 displayTitle 提供更有意义的展示
- 与 DeepResearch 设计对齐：采用相同的 nodeName 驱动模式

**Non-Goals:**
- 不修改 Agent 节点的业务逻辑（路由、查询、排查的核心逻辑不变）
- 不增加新的测试类型（现有测试覆盖重构后的行为）
- 不改造流式输出的实现机制（仍使用 Sinks.Many）

## Decisions

### Decision 1: 节点枚举设计
**选择**：新增 SREAgentNodeName 枚举，包含 router、queryAgent、investigateAgent、admin、tool_call

**理由**：
- 与 DeepResearch 的 NodeNameEnum 保持一致的设计模式
- 枚举值对应 StateGraph 的实际节点名称
- displayTitle 直接映射到前端展示的中文标题

**替代考虑**：直接使用字符串常量而非枚举 → 否，枚举提供类型安全和查找能力

### Decision 2: 工具事件实时发送
**选择**：ObservabilityAspect 在每个 @Tool 方法执行完成后立即发送事件到 SSE

**理由**：
- 用户体验：工具调用逐个实时展示，而非等待所有工具调用结束后批量展示
- 实现简洁：切面直接调用 sink 发送，无需额外的上下文收集逻辑

**替代考虑**：收集到上下文批量发送 → 否，用户无法看到实时的工具调用进度

### Decision 3: 事件结构
**选择**：事件包含 nodeName 和 displayTitle 字段，移除 type 和 stepNumber

**理由**：
- nodeName 是统一标识符，与后端节点对应
- displayTitle 是面向用户的中文标题
- type 是冗余字段，与 nodeName 功能重复

**替代考虑**：保留 type 兼容前端 → 否，重构是一次性更新，前端也需要配合修改

### Decision 4: 前端解析策略
**选择**：参考 DeepResearch，使用 findNode(nodeName) 模式

**理由**：
- 与后端 nodeName 设计一致
- 可以一次性解析完整的事件数组
- 易于扩展新节点类型

**替代考虑**：继续基于 type 解析 → 否，与后端设计不对齐

## Risks / Trade-offs

### Risk 1: 事件顺序变化
**现象**：重构后工具事件的发送时机改变（从切面直接发 → 节点完成时发）
**影响**：前端展示顺序可能与之前不同
**缓解**：设计验证阶段对比新旧事件顺序，确保关键信息（路由决策→工具调用→结果）顺序不变

### Risk 2: 兼容性中断
**现象**：旧版前端无法解析新版事件结构（无 type 字段，有 nodeName）
**影响**：需要同步升级前后端
**缓解**：在 tasks.md 中明确前后端同时部署，或提供临时兼容层

### Risk 3: 切面与节点通信
**现象**：ObservabilityAspect 需要与 SREAgentEventDispatcher 共享上下文
**影响**：需要通过 ThinkingContextHolder 传递事件列表
**缓解**：明确 ThinkingContextHolder 的数据结构，确保线程安全

## Migration Plan

1. **第一阶段**：新增 SREAgentNodeName 枚举和 SREAgentEventDispatcher
2. **第二阶段**：改造 ObservabilityAspect 为收集模式，修改 ThinkingContextHolder
3. **第三阶段**：改造 SREAgentGraphProcess，调用 EventDispatcher 替代原有 resolveContent
4. **第四阶段**：前端 useChat.ts 改造解析逻辑
5. **第五阶段**：端到端测试验证

部署策略：前后端同时部署，因为事件结构变化不兼容单独升级

## Open Questions

1. **Q1**: 工具事件的发送时机是在节点开始时还是节点结束时？
   - **已确定**：实时发送（@Tool 方法执行完成后立即发送）
   - 好处：用户可以看到每个工具调用的实时进度

2. **Q2**: 是否需要为工具事件单独分配 nodeName？
   - 当前设计：tool_call 作为统一节点名
   - 待确认：是否需要根据具体工具名细分（如 tool_call_ontologyQuery）