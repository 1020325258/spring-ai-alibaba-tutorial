## Why

SREmate（05 项目）是数据查询助手，仅支持单轮查询返回数据，无法满足"订单发起合同没个性化报价"等问题的自主排查需求。需要在 SREmate 之外构建一个新的 Agent，能够理解用户的问题描述，通过多轮推理和工具调用完成问题排查。

同时，04-AnalysisAgent 已经验证了 Spring AI Graph 多 Agent 编排的可行性，本项目将复用该架构模式。

## What Changes

1. **新建 SRE-Agent 项目**（06-SRE-Agent）
   - 复制 05-SREmate 作为基础，移除不必要的查询优化
   - 基于 04-AnalysisAgent 的 Graph 架构实现多 Agent 编排

2. **实现多 Agent 编排架构**
   - Supervisor Agent：理解用户意图，路由到合适的子 Agent
   - Query Agent：数据查询（复用 05 的 ontologyQuery 能力）
   - Investigate Agent：问题排查（调用 Skill + 查询）

3. **集成 Skill 机制**
   - 支持 Skill 方式封装排查 SOP
   - 实现 `read_skill` 工具让 Agent 自主加载 Skill

4. **移除 DirectOutput 优化**
   - 多 Agent 编排场景下，子 Agent 返回 JSON 给主 Agent 处理

## Capabilities

### New Capabilities
- `multi-agent-orchestration`: 多 Agent 编排能力，基于 Spring AI Graph 实现 Supervisor 模式
- `investigation-skill`: 问题排查 Skill 框架，支持将排查 SOP 封装为可复用的 Skill
- `agent-communication`: Agent 间通信能力，通过 HTTP 调用 05-SREmate 获取数据

### Modified Capabilities
（无）

## Impact

- 新增项目：`06-SRE-Agent`（与现有 05-SREmate 平级）
- 依赖关系：SRE-Agent 通过 HTTP 调用 05-SREmate 的数据查询能力
- 无破坏性变更
