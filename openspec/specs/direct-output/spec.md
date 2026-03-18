## ADDED Requirements

### Requirement: @DataQueryTool 注解标记直接输出工具
系统 SHALL 提供 `@DataQueryTool` 注解，标记在最外层的用户直调工具上，AOP 切面 (`ObservabilityAspect`) SHALL 通过 `DirectOutputHolder.addResult()` 收集工具返回值，在 LLM 首字节到达时聚合输出并终止流，跳过 LLM 归纳阶段。

实际触发时序：
```
工具执行完成 → AOP addResult() 收集
                    ↓
              LLM 首字节到达 → hasOutput() 检查 → getAndClearAggregated() 聚合
                                                          ↓
                                                    直接输出 → dispose() 终止流
```

#### Scenario: 注解工具结果被收集
- **WHEN** 带有 `@DataQueryTool` 注解的工具方法执行完成
- **THEN** `ObservabilityAspect` 调用 `directOutputHolder.addResult(toolName, result)` 收集结果，标记为 `pending`

#### Scenario: 首字节到达时触发直接输出
- **WHEN** LLM 首字节到达，`directOutputHolder.hasOutput()` 为 true
- **THEN** 调用 `getAndClearAggregated()` 聚合所有收集的结果并输出，随即 `dispose()` 终止流，总耗时约等于 TTFB

#### Scenario: 多结果聚合（Order + Contract 跨层）
- **WHEN** 同一次请求中 `ontologyQuery` 被调用多次（如先查 Order 得到合同列表，再逐个查 Contract 关联数据）
- **THEN** `DirectOutputHolder` 将多次结果合并：Order 结果为主体，Contract 结果中的关联数据（nodes、quotationRelations 等）合并到对应合同记录中，最终输出一个完整的结构

---

### Requirement: 内部工具不得标记注解
系统 SHALL 要求 `@DataQueryTool` 只能标记在用户直调的最外层工具上；内部工具（如 `HttpEndpointClient` 方法）禁止标记该注解。

#### Scenario: 内部工具无注解不触发输出
- **WHEN** `ontologyQuery` 内部调用 `HttpEndpointClient.callPredefinedEndpoint`（无注解）
- **THEN** 中间结果不触发 DirectOutput，最终由 `ontologyQuery` 的结果触发

#### Scenario: 错误标注导致中间结果泄漏（反例）
- **WHEN** 内部工具也被标记 `@DataQueryTool`
- **THEN** 中间结果被捕获，最终结果丢失——这是违规行为，不应发生

---

### Requirement: 性能提升可观测
系统 SHALL 在日志中输出 TTFB、工具耗时、总耗时，以及 DirectOutput 是否生效的标记。

#### Scenario: 性能日志输出
- **WHEN** 工具调用完成
- **THEN** 日志包含 `⏱ 首字节: Xms | 工具耗时: Xms | 总耗时: Xms` 和 `[DirectOutput] ✓ 已生效` 标记
