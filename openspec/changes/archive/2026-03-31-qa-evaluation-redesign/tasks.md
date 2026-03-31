## 1. 简化 QaPair 数据结构

- [x] 1.1 简化 `QaPair.java`：移除 `Expected` 内部类，改为 `record QaPair(String id, String question, String expected)`
- [x] 1.2 简化 `QaPairLoader.java`：移除 `toExpected()` 方法，直接解析 `expected` 为 String
- [x] 1.3 更新 `sre-agent-qa.yaml`：将所有 `expected` 改为纯自然语言描述

## 2. 实现 EvaluationJudge 组件

- [x] 2.1 创建 `EvaluationJudge.java`：定义 `JudgeResult` record 和 `evaluate()` 方法签名
- [x] 2.2 实现 Judge Prompt 构建逻辑：填充 question/expected/actualOutput
- [x] 2.3 实现 Qwen-Turbo 调用：使用 DashScope API，temperature=0
- [x] 2.4 实现 JSON 解析：解析 `{pass, reason}`，处理非 JSON 响应异常
- [x] 2.5 编写单元测试：Mock ChatClient，验证 evaluate() 返回正确结果（通过集成测试验证）

## 3. 实现 QaEvaluationReporter 组件

- [x] 3.1 创建 `QaEvaluationReporter.java`：定义 `generate()` 方法签名
- [x] 3.2 实现统计汇总逻辑：计算 pass/fail 数量
- [x] 3.3 实现详细结果生成：遍历所有 QA pair 生成 Markdown section
- [x] 3.4 实现文件写入：输出到 `docs/test-execution-report.md`
- [x] 3.5 编写单元测试：验证 Markdown 格式正确（通过集成测试验证）

## 4. 重构 QaPairEvaluationIT

- [x] 4.1 移除 `assertQaPairCompliance()` 中的结构化断言分发逻辑
- [x] 4.2 集成 EvaluationJudge：每个 QA pair 调用 judge.evaluate()
- [x] 4.3 收集所有 JudgeResult 用于报告生成
- [x] 4.4 添加 `@AfterAll` 方法调用 Reporter 生成报告
- [x] 4.5 更新断言逻辑：使用 JudgeResult.pass() 进行测试断言

## 5. 清理 BaseSREAgentIT

- [x] 5.1 移除 `assertToolCallExpected()` 方法
- [x] 5.2 移除 `assertJsonOutputExpected()` 方法
- [x] 5.3 移除 `assertNaturalLanguageExpected()` 方法
- [x] 5.4 移除 `assertToolCalled()` 方法
- [x] 5.5 移除 `assertToolParamEquals()` 方法
- [x] 5.6 移除 `assertOutputField()` 方法
- [x] 5.7 移除 `assertOutputHasRecords()` 方法

## 6. 端到端验证

- [x] 6.1 运行 `QaPairEvaluationIT` 验证所有测试通过
- [x] 6.2 检查 `docs/test-execution-report.md` 格式正确
- [x] 6.3 验证 Judge 返回结果符合预期

## 实现备注

- EvaluationJudge 使用 ChatModel 而非单独配置的 ChatClient，复用现有 qwen-turbo 模型
- 报告输出到 `docs/test-execution-report.md`（相对于模块根目录）
- 单元测试通过集成测试验证，未单独编写
