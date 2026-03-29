## Why

SREmate 作为"Spring AI Alibaba 教程"系列的第 5 个示例项目，历经多轮架构迭代形成了本体论驱动的查询引擎——这是一套值得沉淀的设计模式。归档初版是为了在进入 v2 规划前，固化已有的架构决策和边界认知，防止历史背景丢失。

## What Changes

本 change 不修改任何代码，只做文档归档：

- **记录**：本体论查询引擎（OntologyQueryEngine）的设计决策与演进过程
- **记录**：DDD 分层架构下各层职责的最终约定
- **记录**：已支持的实体列表与查询能力边界
- **记录**：集成测试体系的现状与覆盖范围
- **记录**：已知的技术负债与 v1 未解决的问题

## Capabilities

### New Capabilities

- `ontology-query-engine`: 本体论驱动的并行查询引擎 —— 核心能力，包含 entity/relation 模型、路径规划、并行执行
- `entity-gateway`: Gateway 模式的实体数据获取层 —— 各 domain 的 Gateway 实现约定
- `direct-output`: `@DataQueryTool` 注解驱动的直接输出机制 —— 绕过 LLM 二次处理
- `integration-test`: 集成测试体系 —— 基于真实 LLM 调用的意图识别和工具调用验证
- `knowledge-query`: 知识库查询能力 —— 向量检索 + Spring AI

### Modified Capabilities

（无，初版归档无修改项）

## Impact

- **影响范围**：仅文档，不涉及代码变更
- **受益对象**：v2 规划者、新加入的开发者、AI 辅助开发上下文
- **关联目录**：`05-SREmate/src/`、`05-SREmate/CLAUDE.md`、`05-SREmate/README.md`
