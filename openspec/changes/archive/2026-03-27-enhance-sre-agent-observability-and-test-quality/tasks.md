## 1. 修复 sre-agent.md 提示词

- [x] 1.1 在"响应原则"章节前增加意图分类规则：列出"排查/诊断"关键词触发诊断模式，"查询/查看/列出"关键词触发查询模式
- [x] 1.2 为诊断模式定义四段式输出格式模板：`【数据查询】→【分析】→【结论】→【建议】`，并明确禁止对诊断类请求直接输出原始 JSON
- [x] 1.3 将现有"直接输出 JSON"规则限定为仅适用于纯查询意图（在规则标题中加入限定词）
- [x] 1.4 在示例对话章节补充一个排查类请求的示例，展示四段式输出格式

## 2. BaseSREAgentIT 新增 LLM-as-Judge 能力

- [x] 2.1 在 `BaseSREAgentIT` 中注入 `ChatModel`（`@Autowired private ChatModel chatModel`）
- [x] 2.2 编写 judge prompt 常量：判断输出是否包含自然语言分析和结论，要求返回 `{"pass": true/false, "reason": "..."}`
- [x] 2.3 实现 `assertOutputIsInvestigationConclusion(String response)` 方法：调用 `chatModel`，解析 JSON 结果，失败时抛出 `AssertionError`（错误消息包含 reason 和输出前200字）
- [x] 2.4 实现降级逻辑：当 judge 返回内容无法解析为 JSON 时，退化为检查响应不以 `{` 或 `[` 开头

## 3. InvestigateAgentIT 补充输出质量断言

- [x] 3.1 在 `investigate_should_pass_correct_params` 中，在参数断言之后添加 `assertOutputIsInvestigationConclusion(response)`
- [x] 3.2 在 `investigate_missing_personal_quote` 中，在工具调用断言之后添加 `assertOutputIsInvestigationConclusion(response)`
- [x] 3.3 在 `investigate_sales_contract_sign_dialog` 中，在工具调用断言之后添加 `assertOutputIsInvestigationConclusion(response)`

## 4. 验证

- [x] 4.1 运行 `InvestigateAgentIT` 全部三个测试，确认全部通过（含新增的 judge 断言）
- [x] 4.2 运行 `QueryAgentIT` 全部测试，确认查询类场景仍输出原始 JSON，无回归
- [x] 4.3 运行 `SkillMechanismIT` 验证 Skill 机制无影响
