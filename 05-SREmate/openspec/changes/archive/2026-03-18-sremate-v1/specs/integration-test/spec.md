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
`BaseSREIT` SHALL 提供 `ask()`、`assertToolCalled()`、`assertToolNotCalled()`、`assertAllToolsSuccess()` 方法，所有集成测试 SHALL 继承该基类。

#### Scenario: ask 方法触发真实 LLM 调用
- **WHEN** 调用 `ask("问题")`
- **THEN** 向真实 LLM API（Qwen）发送请求，捕获工具调用记录
