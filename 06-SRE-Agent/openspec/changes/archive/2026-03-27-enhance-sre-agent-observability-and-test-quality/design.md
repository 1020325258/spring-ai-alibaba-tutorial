## Context

SRE-Agent 当前使用 `supervisorAgent`（绑定 `ontologyQuery`、`readSkill` 等工具的 `ChatClient`）处理用户请求。`sre-agent.md` 提示词中存在一条全局性的"直接输出JSON"规则，原意是保证数据查询结果的准确性，但该规则被 LLM 错误地应用于"排查"意图，导致排查类请求也输出原始 JSON。

测试侧，`BaseSREAgentIT` 已有工具调用参数验证能力，但缺少对最终输出内容质量的评估手段。

## Goals / Non-Goals

**Goals:**
- 修复 `sre-agent.md` 提示词，使 LLM 对"排查"意图输出自然语言分析结论，对"查询"意图输出原始 JSON
- 在测试框架中引入 LLM-as-Judge 机制，验证排查类输出质量
- 三个 `InvestigateAgentIT` 测试均通过输出质量断言

**Non-Goals:**
- 不修改 StateGraph 节点（`InvestigateAgentNode` 仍为 stub，不在本次范围内）
- 不升级模型，不改变部署配置
- 不修改 `QueryAgentIT` / `SkillMechanismIT` 测试

## Decisions

### 决策一：Intent 分类方式 —— 关键词规则 vs. 额外 LLM 分类调用

**选择**：在提示词中使用关键词规则直接区分意图。

**理由**：增加一次额外 LLM 调用会增加延迟和成本。用户问题的意图通过关键词("排查/诊断/为什么/原因" vs "查询/查看/列出/显示")通常是清晰的。提示词中明确列出关键词列表，配合示例对照表，足以覆盖常见场景。若 LLM 仍误判，可通过调整关键词列表迭代修正。

**备选**：用 SupervisorNode 做独立意图分类调用 —— 过度工程，本次不采用。

### 决策二：Judge 使用的 ChatClient 实例

**选择**：在 `BaseSREAgentIT` 中直接注入 `ChatModel`（底层模型接口），而非复用 `supervisorAgent`。

**理由**：`supervisorAgent` 绑定了业务工具（`ontologyQuery` 等），judge prompt 中包含业务数据时可能触发工具调用，干扰 `TracingService` 的调用计数，导致 `captureNewToolCalls` 逻辑错乱。`ChatModel` 是无工具绑定的原始接口，直接调用底层模型，隔离性更好。

Spring AI 中 `ChatModel` 由 auto-configuration 自动注册为 Bean，可直接 `@Autowired`。

### 决策三：Judge 结果的可信度处理

**选择**：直接信任 judge 结果，judge 判定失败则测试失败；judge 自身解析异常时降级为简单字符串断言（不以 `{`/`[` 开头）。

**理由**：降级策略保证了 judge 异常（如模型超时、输出格式异常）不会使测试产生假阳性，同时主路径保持严格。用户明确表示接受直接信任 judge 并通过迭代调整 judge prompt 来提升准确率。

### 决策四：诊断模式输出格式

**选择**：结构化 Markdown，固定四段式：

```
**【数据查询】** 调用 ontologyQuery 获取 ... 数据
**【分析】** 关键发现：...
**【结论】** 问题原因/数据状态：...
**【建议】**（可选）下一步操作：...
```

**理由**：结构化格式使 judge 的评估标准更稳定，也让 SRE 值班人员更快定位关键信息。固定段落标题有助于 LLM 形成稳定的输出模式。

## Risks / Trade-offs

- **[风险] Judge 偶发误判** → 通过迭代调整 judge prompt 的评估标准来修正；测试失败时日志会打印完整输出，便于人工判断是否为误判
- **[风险] 提示词修改导致查询类回归** → 修改后须运行 `QueryAgentIT` 全量验证，确保"查询"意图仍输出 JSON
- **[trade-off] 每个排查测试多一次 LLM 调用** → 增加约 1-2 秒测试时间和少量 token 成本；在接受范围内，且仅影响 `InvestigateAgentIT` 的三个测试
- **[trade-off] 关键词分类的边界模糊** → "排查XX数据"之类的混合表述可能被误分类；通过在提示词中给出足够多的对照示例降低风险

## Open Questions

无。
