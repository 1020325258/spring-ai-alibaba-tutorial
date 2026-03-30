# Tasks: 重构 SRE-Agent 图执行架构

## Analysis

- [x] 分析 SREAgentGraph 冗余问题
- [x] 分析流式输出 null 根因（StreamingOutput.chunk() vs state["result"]）
- [x] 分析测试复杂度来源（Reactor 订阅时序）

## Implementation

- [x] 删除 SREAgentGraph.java
- [x] 删除 SREAgentGraphConfiguration 中的 sreAgentGraph Bean
- [x] 修复 ChatController 中 RunnableConfig null 问题
- [x] 修复 buildStreamingContent() fallback 逻辑（router + agent 节点）
- [x] 简化 buildNormalContent()（跳过 router 和 agent，由 buildStreamingContent 处理）
- [x] 新增 streamAndCollect() 方法
- [x] 新增 ChatStreamIT 测试类（4 个测试用例）
- [x] 删除旧测试：BaseSREAgentIT, InvestigateAgentIT, QueryAgentIT, SkillMechanismIT, ReadSkillToolIT, SkillLoadingIT

## Verification

- [x] ChatStreamIT 4/4 测试通过
- [x] build 成功（无编译错误）