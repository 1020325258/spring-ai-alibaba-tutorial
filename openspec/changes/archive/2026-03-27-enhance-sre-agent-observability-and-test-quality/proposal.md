## Why

`InvestigateAgentIT` 的排查类测试输出的是原始 JSON 数据而非自然语言排查结论，原因是 `sre-agent.md` 提示词的"直接输出JSON"规则没有区分"查询"和"排查"两种意图，导致 LLM 对所有包含 `ontologyQuery` 调用的请求都输出原始 JSON。同时，测试断言层只验证工具调用参数，缺少对输出内容质量的评估，无法有效检测此类回归。

## What Changes

- **修复 `sre-agent.md` 提示词**：明确区分"排查/诊断"意图与"查询"意图，为诊断模式定义结构化输出格式（数据查询 → 分析 → 结论 → 建议），将"直接输出JSON"规则限定为仅适用于纯查询意图
- **新增 LLM-as-Judge 评估能力**：在 `BaseSREAgentIT` 中注入无工具绑定的 `ChatModel`，新增 `assertOutputIsInvestigationConclusion()` 方法，通过二次 LLM 调用判断输出是否为自然语言排查结论
- **补充排查测试输出质量断言**：在 `InvestigateAgentIT` 的三个测试方法中调用 judge 方法，验证 LLM 输出不是原始 JSON 而是真实的排查结论

## Capabilities

### New Capabilities

- `llm-as-judge`：在集成测试中通过第二个 LLM 实例评估主 Agent 输出质量，判断输出是否符合"排查结论"的定义（包含自然语言分析和结论，而非原始 JSON 数据）

### Modified Capabilities

无已有 spec 需要变更。

## Impact

- `src/main/resources/prompts/sre-agent.md`：提示词修改，影响所有排查类请求的输出格式
- `src/test/java/com/yycome/sreagent/e2e/BaseSREAgentIT.java`：新增 judge 断言方法，注入 `ChatModel`
- `src/test/java/com/yycome/sreagent/e2e/InvestigateAgentIT.java`：三个测试方法增加输出质量断言
- 无 API 变更，无外部依赖变更，不影响 `QueryAgentIT` / `SkillMechanismIT`
