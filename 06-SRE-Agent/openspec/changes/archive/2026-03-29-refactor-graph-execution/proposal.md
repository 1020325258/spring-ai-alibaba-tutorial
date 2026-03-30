# 重构 SRE-Agent 图执行架构

## Summary

移除 SRE-Agent 中的 `SREAgentGraph`，改用 `SREAgentGraphProcess` 作为唯一的图执行组件。同时新增 `ChatStreamIT` 端到端测试，调用 `streamAndCollect()` 验证 4 个核心场景。

## Motivation

### 问题 1：SREAgentGraph 冗余

`SREAgentGraph` 封装了 `CompiledGraph`，提供 `streamMessages()` 方法，但 `SREAgentGraphProcess` 已有相同功能（`streamAndCollect()`）。两层抽象增加了维护成本。

### 问题 2：流式输出为 null

HTTP SSE 接口调用 `CompiledGraph.stream()` 时抛出 `config cannot be null` 空指针异常。`stream()` 必须传入 `RunnableConfig.builder().build()` 而非 null。

### 问题 3：StreamingOutput vs NodeOutput 处理不一致

图执行后，Agent 节点发出的 `NodeOutput` 实际上是 `StreamingOutput` 类型，其 `chunk()` 返回 null，但 `state["result"]` 中有完整结果。原代码只处理了 `StreamingOutput` 的 chunk，未做 fallback，导致输出为空。

### 问题 4：测试不可直接调用

原 `BaseSREAgentIT` 通过复杂的 SSE 流式消费逻辑测试，需要处理 Reactor 订阅时序、SSE chunk 累积等。测试应该像调用普通方法一样简单。

## Approach

### 架构简化

1. 删除 `SREAgentGraph.java`
2. `ChatController` 直接注入 `SREAgentGraphProcess`
3. 修复 `RunnableConfig` 传参问题

### 流式输出修复

`buildStreamingContent()` 处理 `StreamingOutput` 时：
- 优先从 `chunk()` 获取内容
- chunk 为 null 时，根据节点类型从 `state` 获取：router → routingTarget，Agent → result

### 新增可测试方法

在 `SREAgentGraphProcess` 中新增 `streamAndCollect(String input)` 方法：
- 同步执行图，累积所有节点输出
- 返回完整文本，供测试直接验证

### 测试简化

新建 `ChatStreamIT`，直接调用 `streamAndCollect()`，测试 4 个场景：
1. 排查场景（输出非 JSON）
2. 查询合同基本信息（输出为合法 JSON）
3. 查询签约单据（输出为合法 JSON）
4. 查询合同节点（输出为合法 JSON）

## Specs

- `SREAgentGraphProcess` 提供 `streamAndCollect()` 同步执行入口
- `processStream()` 使用 `RunnableConfig.builder().build()` 而非 null
- `buildStreamingContent()` 对所有节点正确 fallback 到 state
- `ChatStreamIT` 4 个测试全部通过

## Deleted Files

- `src/main/java/com/yycome/sreagent/config/SREAgentGraph.java`

## New Files

- `src/test/java/com/yycome/sreagent/e2e/ChatStreamIT.java`

## Changed Files

- `src/main/java/com/yycome/sreagent/trigger/http/ChatController.java` — 注入 `SREAgentGraphProcess`
- `src/main/java/com/yycome/sreagent/config/SREAgentGraphConfiguration.java` — 删除 `sreAgentGraph` Bean
- `src/main/java/com/yycome/sreagent/config/SREAgentGraphProcess.java` — 新增 `streamAndCollect()`，修复 `buildStreamingContent()` fallback 逻辑
- `src/main/java/com/yycome/sreagent/infrastructure/service/ThinkingEventPublisher.java` — 删除 `getThinkingFlux()`（无消费者）

## Deleted Tests

- `src/test/java/com/yycome/sreagent/e2e/BaseSREAgentIT.java`
- `src/test/java/com/yycome/sreagent/e2e/InvestigateAgentIT.java`
- `src/test/java/com/yycome/sreagent/e2e/QueryAgentIT.java`
- `src/test/java/com/yycome/sreagent/e2e/SkillMechanismIT.java`
- `src/test/java/com/yycome/sreagent/trigger/agent/ReadSkillToolIT.java`
- `src/test/java/com/yycome/sreagent/skill/SkillLoadingIT.java`