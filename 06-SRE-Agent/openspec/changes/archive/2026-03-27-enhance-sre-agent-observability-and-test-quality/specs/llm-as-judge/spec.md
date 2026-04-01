## ADDED Requirements

### Requirement: 排查意图输出自然语言结论
`sre-agent.md` 提示词 SHALL 区分"排查/诊断"意图与"查询"意图，对排查意图必须输出包含分析和结论的自然语言，不得直接输出原始 JSON。

#### Scenario: 排查类请求输出结构化分析结论
- **WHEN** 用户输入包含"排查"、"诊断"、"为什么"、"原因"等关键词
- **THEN** LLM 输出 Markdown 格式的分析结论，包含"分析"和"结论"两个语义段落，不以 `{` 或 `[` 开头

#### Scenario: 查询类请求仍输出原始 JSON
- **WHEN** 用户输入包含"查询"、"查看"、"列出"、"显示"等关键词
- **THEN** LLM 输出原始 JSON 字符串，不包含额外说明文字

### Requirement: BaseSREAgentIT 提供 LLM-as-Judge 断言方法
`BaseSREAgentIT` SHALL 提供 `assertOutputIsInvestigationConclusion(String response)` 方法，通过注入的 `ChatModel`（无工具绑定）调用 LLM 评估输出质量。

#### Scenario: 输出为自然语言排查结论时断言通过
- **WHEN** `assertOutputIsInvestigationConclusion` 收到包含自然语言分析和结论的字符串
- **THEN** judge LLM 返回 `{"pass": true, ...}`，方法正常返回，测试继续

#### Scenario: 输出为原始 JSON 时断言失败
- **WHEN** `assertOutputIsInvestigationConclusion` 收到以 `{` 或 `[` 开头的原始 JSON 字符串
- **THEN** judge LLM 返回 `{"pass": false, "reason": "..."}` 或方法通过降级逻辑抛出 `AssertionError`，错误消息包含 judge 给出的理由和输出前200字

#### Scenario: judge 调用异常时降级断言
- **WHEN** judge LLM 调用超时或返回无法解析的内容
- **THEN** 方法降级为检查响应不以 `{` 或 `[` 开头，并在断言消息中说明已降级

### Requirement: InvestigateAgentIT 所有排查测试包含输出质量断言
`InvestigateAgentIT` 的三个测试方法 SHALL 在验证工具调用参数之后，调用 `assertOutputIsInvestigationConclusion` 验证输出质量。

#### Scenario: investigate_should_pass_correct_params 通过质量断言
- **WHEN** 运行 `investigate_should_pass_correct_params` 测试
- **THEN** 在验证 `ontologyQuery` 的 `entity=Order` 参数之后，`assertOutputIsInvestigationConclusion` 也通过

#### Scenario: investigate_missing_personal_quote 通过质量断言
- **WHEN** 运行 `investigate_missing_personal_quote` 测试
- **THEN** 在验证工具调用之后，`assertOutputIsInvestigationConclusion` 也通过

#### Scenario: investigate_sales_contract_sign_dialog 通过质量断言
- **WHEN** 运行 `investigate_sales_contract_sign_dialog` 测试
- **THEN** 在验证工具调用之后，`assertOutputIsInvestigationConclusion` 也通过
