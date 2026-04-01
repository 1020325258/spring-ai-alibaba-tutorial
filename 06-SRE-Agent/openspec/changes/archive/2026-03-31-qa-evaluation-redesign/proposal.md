## Why

当前 `QaPairEvaluationIT` 的评估方式暴露了实现细节（`tool_call`、`json_output` 类型），维护成本高且不够直观。业务人员难以直接维护测试用例，每次提示词或工具调整都需要同步修改 YAML 结构。

通过引入 LLM-as-Judge 机制，将评估简化为纯自然语言的问答对维护，降低维护门槛，让测试更贴近业务语义。

## What Changes

- **简化 QaPair 结构**：移除 `type/tool/params/mustContain` 结构化字段，`expected` 改为纯自然语言描述
- **新增 EvaluationJudge 组件**：使用 Qwen-Turbo 进行语义评估，返回二元判定结果
- **新增 QaEvaluationReporter 组件**：测试完成后生成 Markdown 报告，包含原始输入、输出、评估结果
- **移除结构化断言逻辑**：删除 `BaseSREAgentIT` 中的 `tool_call/json_output` 验证方法

## Capabilities

### New Capabilities

- `qa-evaluation-judge`: 语义评估能力，使用 LLM 判断 Agent 输出是否符合自然语言预期描述
- `qa-evaluation-report`: 测试报告生成能力，输出完整 Markdown 文档

### Modified Capabilities

无。这是新增能力，不修改现有 spec 行为。

## Impact

- **代码变更**：
  - `QaPair.java` 简化为 `id + question + expected`
  - `QaPairLoader.java` 简化解析逻辑
  - `QaPairEvaluationIT.java` 移除结构化断言，集成 Judge 调用
  - `BaseSREAgentIT.java` 移除 `tool_call/json_output` 验证方法
  - 新增 `EvaluationJudge.java` 和 `QaEvaluationReporter.java`
- **依赖**：无新增外部依赖，复用现有 Spring AI ChatClient
- **配置**：Qwen-Turbo 模型硬编码在 `EvaluationJudge` 中，无需修改 application.yml
