## ADDED Requirements

### Requirement: 集成测试验证工具调用行为
集成测试 SHALL 验证 LLM 是否调用了正确的工具，而非验证 LLM 的输出内容。

#### Scenario: 断言指定工具被调用
- **WHEN** 调用 `ask("xxx下的合同数据")`
- **THEN** `assertToolCalled("ontologyQuery")` 通过，且工具调用状态为成功

#### Scenario: 断言工具未被调用
- **WHEN** 输入不应触发某工具的自然语言
- **THEN** `assertToolNotCalled("queryContractsByOrderId")` 通过

---

### Requirement: 测试覆盖意图识别准确性
集成测试 SHALL 覆盖编号格式识别（订单号 vs 合同号）和关键词触发（"节点"、"签约单据"等）两类场景。

#### Scenario: 订单号触发 Order 实体
- **WHEN** 问题包含 15 位以上纯数字
- **THEN** LLM 调用 `ontologyQuery` 时 `entity=Order`

#### Scenario: 合同号触发 Contract 实体
- **WHEN** 问题包含 C 前缀数字编号
- **THEN** LLM 调用 `ontologyQuery` 时 `entity=Contract`

---

### Requirement: 代码与提示词同步验证
集成测试 SHALL 作为提示词与代码同步的最后一道防线，当 `sre-agent.md` 与工具参数不同步时，集成测试 SHALL 失败。

#### Scenario: 提示词参数与代码不一致导致失败
- **WHEN** 提示词中写 `queryScope=form`，但代码已删除该别名
- **THEN** 集成测试 `assertAllToolsSuccess()` 失败，暴露不一致问题

---

### Requirement: 测试基础类提供统一能力
`BaseSREIT` SHALL 提供以下方法，所有集成测试 SHALL 继承该基类：

| 方法 | 说明 |
|------|------|
| `ask(question)` | 触发真实 LLM 调用，捕获工具调用记录 |
| `assertToolCalled(toolName)` | 断言指定工具被调用且成功 |
| `assertToolNotCalled(toolName)` | 断言指定工具未被调用 |
| `assertAllToolsSuccess()` | 断言所有工具调用都成功 |
| `assertOntologyQueryParams(entity, queryScope)` | 断言 ontologyQuery 的 entity 和 queryScope 参数（entity 按 value 格式自动修正后验证） |
| `assertToolParamEquals(toolName, key, value)` | 断言工具调用参数中某 key 的值精确匹配 |
| `assertToolParamEqualsIgnoreOrder(toolName, key, value)` | 断言逗号分隔的参数值与期望值相同（忽略顺序） |
| `getToolParams(toolName)` | 获取指定工具最后一次调用的参数 Map |

#### Scenario: ask 方法触发真实 LLM 调用
- **WHEN** 调用 `ask("问题")`
- **THEN** 向真实 LLM API（Qwen）发送请求，捕获工具调用记录，自动写入 `docs/test-execution-report.md`

#### Scenario: assertOntologyQueryParams 含自动 entity 修正
- **WHEN** LLM 传入 `entity=Order`，`value=C1767150648920281`（C 开头）
- **THEN** 工具内部自动修正为 `entity=Contract`；`assertOntologyQueryParams("Contract", ...)` 通过，`assertOntologyQueryParams("Order", ...)` 失败

---

### Requirement: 修改工具参数后必须同步提示词
每次修改工具参数的合法取值范围（增删枚举值、删除别名、重命名参数）时，开发者 SHALL 同步检查并更新 `sre-agent.md`，然后运行集成测试验证 LLM 行为。

> **教训来源（2026-03-17）**：删除 `OntologyQueryTool` 中的 `SCOPE_ALIAS`（`form→ContractForm` 等简写）时，只改了 Java 代码，未同步提示词。提示词决策表仍写 `queryScope=form`，LLM 按旧格式调用，导致 `contractForm_shouldCallOntologyQuery` 集成测试失败。

#### Scenario: 删除参数别名后提示词未更新
- **WHEN** 代码中删除了 `queryScope` 的别名（如 `form`），但 `sre-agent.md` 仍写旧格式
- **THEN** `assertAllToolsSuccess()` 失败，日志显示 `找不到路径: Contract -> form`

#### Scenario: 同步提示词后测试恢复
- **WHEN** 将 `sre-agent.md` 中的 `queryScope=form` 改为 `queryScope=ContractForm`
- **THEN** 集成测试重新通过

---

### Requirement: 新增工具后更新提示词
新增 `@Tool` 方法后，开发者 SHALL 在 `sre-agent.md` 的"可用工具"和"快速决策表"中补充对应说明。

#### Scenario: 新工具未添加到提示词决策表
- **WHEN** 新增了工具方法但未更新提示词
- **THEN** LLM 不知道新工具存在，对应场景的集成测试失败
