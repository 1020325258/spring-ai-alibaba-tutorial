## ADDED Requirements

### Requirement: @DataQueryTool 注解标记直接输出工具
系统 SHALL 提供 `@DataQueryTool` 注解，标记在最外层的用户直调工具上，AOP 切面 SHALL 捕获该工具的返回值并直接流式输出，跳过 LLM 归纳阶段。

#### Scenario: 注解工具结果直接输出
- **WHEN** 带有 `@DataQueryTool` 注解的工具方法执行完成
- **THEN** 工具结果通过 `DirectOutputHolder` 直接推送给前端，不再经过 LLM 二次处理

#### Scenario: 直接输出后终止流
- **WHEN** DirectOutput 生效
- **THEN** 立即 `dispose()` 终止 LLM 响应流，总耗时约等于首字节时间（TTFB）

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
